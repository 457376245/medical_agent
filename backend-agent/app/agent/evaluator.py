"""Read-only answer quality evaluator for chat turns."""

from __future__ import annotations

import json
import logging
import asyncio
import time
from typing import Any, Callable, Literal

from agents import Agent, ModelSettings, Runner
from pydantic import BaseModel, Field

from app.config import ANSWER_EVALUATOR_TIMEOUT_SECONDS, DEFAULT_AGENT_MODEL

LOGGER = logging.getLogger(__name__)

RiskLevel = Literal["low", "medium", "high"]
IssueSeverity = Literal["low", "medium", "high"]
GroundednessStatus = Literal["grounded", "partial", "ungrounded"]
EVALUATOR_RUBRIC_VERSION = "grounded-evaluator-v2"


class EvaluationIssue(BaseModel):
    severity: IssueSeverity
    message: str


class EvaluationAgentOutput(BaseModel):
    overall_score: int = Field(ge=0, le=100)
    risk_level: RiskLevel
    summary: str
    issues: list[EvaluationIssue] = Field(default_factory=list)
    suggestions: list[str] = Field(default_factory=list)
    groundedness_status: GroundednessStatus = "partial"
    citation_coverage: int = Field(default=0, ge=0, le=100)
    high_risk_omissions: list[str] = Field(default_factory=list)
    unconfirmed_facts: list[str] = Field(default_factory=list)


def build_context_summary(metadata: dict[str, Any]) -> dict[str, Any]:
    summary: dict[str, Any] = {}
    for key in (
        "disease_name",
        "record_title",
        "record_date",
        "source_type",
        "workflow",
        "scenario",
        "audience",
        "urgency_level",
        "context_status",
    ):
        value = metadata.get(key)
        if value is None or value == "":
            continue
        summary[key] = value
    return summary


def unavailable_evaluation(*, error: str = "复核暂不可用", latency_ms: float = 0.0) -> dict[str, Any]:
    return {
        "status": "unavailable",
        "error": error,
        "rubric_version": EVALUATOR_RUBRIC_VERSION,
        "latency_ms": round(latency_ms, 3),
    }


async def evaluate_answer(
    *,
    user_message: str,
    assistant_answer: str,
    metadata: dict[str, Any] | None = None,
    grounded_context: dict[str, Any] | None = None,
    invoke_json: Any | None = None,
    runner: Any | None = None,
) -> dict[str, Any]:
    answer = (assistant_answer or "").strip()
    if not answer:
        return unavailable_evaluation()

    metadata = metadata or {}
    prompt = {
        "user_message": user_message,
        "context_summary": build_context_summary(metadata),
        "grounded_context": grounded_context or {"status": "unavailable", "evidence": []},
        "assistant_answer": answer,
        "rubric_version": EVALUATOR_RUBRIC_VERSION,
    }
    user_input = json.dumps(prompt, ensure_ascii=False)

    started = time.perf_counter()
    try:
        result = await asyncio.wait_for(
            _evaluate(user_input=user_input, invoke_json=invoke_json, runner=runner),
            timeout=ANSWER_EVALUATOR_TIMEOUT_SECONDS,
        )
        result["rubric_version"] = EVALUATOR_RUBRIC_VERSION
        result["latency_ms"] = round((time.perf_counter() - started) * 1000, 3)
        return result
    except Exception as exc:
        LOGGER.warning("answer evaluation failed: %s", exc)
        return unavailable_evaluation(latency_ms=(time.perf_counter() - started) * 1000)


async def _evaluate(*, user_input: str, invoke_json: Any | None, runner: Any | None) -> dict[str, Any]:
    try:
        if invoke_json is not None:
            messages = [
                {"role": "system", "content": _EVALUATOR_INSTRUCTIONS},
                {"role": "user", "content": user_input},
            ]
            raw = await _call_invoke_json(invoke_json, messages)
            parsed = _load_json_object(raw)
            return _normalize_available(parsed)

        parsed = await _run_evaluator_agent(
            user_input=user_input,
            runner=runner or Runner,
        )
        return _normalize_available(parsed)
    except Exception:
        raise


_EVALUATOR_INSTRUCTIONS = (
    "你是医疗对话回答质量复核员。只根据给定问题、实际使用的脱敏 evidence 和助手最终回答进行只读评估。"
    "不要调用工具，不要补充新的医学事实。必须检查：回答中的医学事实是否由 evidence_id 支持、关键事实是否引用 evidence_id、"
    "过敏/用药/红旗是否遗漏、PENDING 记忆是否被误写成确定事实。输出 overall_score、risk_level、summary、issues、suggestions、"
    "groundedness_status(grounded|partial|ungrounded)、citation_coverage(0-100)、high_risk_omissions、unconfirmed_facts。"
)


async def _call_invoke_json(
    invoke_json: Callable[..., Any],
    messages: list[dict[str, Any]],
) -> str:
    result = invoke_json(messages)
    if hasattr(result, "__await__"):
        return await result
    return result


async def _run_evaluator_agent(*, user_input: str, runner: Any) -> dict[str, Any]:
    agent = Agent(
        name="answer-evaluator",
        instructions=_EVALUATOR_INSTRUCTIONS,
        model=DEFAULT_AGENT_MODEL,
        model_settings=ModelSettings(temperature=0, max_tokens=2048, truncation="auto"),
        tools=[],
        output_type=EvaluationAgentOutput,
    )
    run_result = await runner.run(agent, input=user_input, max_turns=1)
    structured = run_result.final_output_as(EvaluationAgentOutput)
    return structured.model_dump()


def _load_json_object(raw: str) -> dict[str, Any]:
    text = (raw or "").strip()
    if not text:
        raise ValueError("empty evaluator output")
    parsed = json.loads(text)
    if not isinstance(parsed, dict):
        raise ValueError("evaluator output must be an object")
    return parsed


def _normalize_available(payload: dict[str, Any]) -> dict[str, Any]:
    score = int(payload.get("overall_score", 0))
    score = max(0, min(100, score))
    risk = str(payload.get("risk_level") or "medium").strip().lower()
    if risk not in {"low", "medium", "high"}:
        risk = "medium"
    summary = str(payload.get("summary") or "").strip() or "已完成回答质量复核。"
    issues_raw = payload.get("issues")
    issues: list[dict[str, str]] = []
    if isinstance(issues_raw, list):
        for item in issues_raw:
            if not isinstance(item, dict):
                continue
            severity = str(item.get("severity") or "low").strip().lower()
            if severity not in {"low", "medium", "high"}:
                severity = "low"
            message = str(item.get("message") or "").strip()
            if message:
                issues.append({"severity": severity, "message": message})
    suggestions_raw = payload.get("suggestions")
    suggestions: list[str] = []
    if isinstance(suggestions_raw, list):
        for item in suggestions_raw:
            text = str(item or "").strip()
            if text:
                suggestions.append(text)
    return {
        "status": "available",
        "overall_score": score,
        "risk_level": risk,
        "summary": summary,
        "issues": issues,
        "suggestions": suggestions,
        "groundedness_status": str(payload.get("groundedness_status") or "partial")
        if str(payload.get("groundedness_status") or "partial") in {"grounded", "partial", "ungrounded"}
        else "partial",
        "citation_coverage": max(0, min(100, int(payload.get("citation_coverage", 0) or 0))),
        "high_risk_omissions": _string_list(payload.get("high_risk_omissions")),
        "unconfirmed_facts": _string_list(payload.get("unconfirmed_facts")),
    }


def build_grounded_evaluation_context(bundle: dict[str, Any] | None, status: str | None) -> dict[str, Any]:
    if not isinstance(bundle, dict):
        return {"status": status or "unavailable", "evidence": [], "pending_memories": []}
    evidence = []
    for item in bundle.get("evidence_ledger", []):
        if not isinstance(item, dict):
            continue
        evidence.append({
            "evidence_id": item.get("evidence_id"),
            "category": item.get("category"),
            "summary": item.get("summary"),
            "verification_status": item.get("verification_status"),
            "observed_at": item.get("observed_at"),
        })
    pending = [
        {
            "field_path": item.get("field_path"),
            "value_text": item.get("value_text"),
            "risk_level": item.get("risk_level"),
            "verification_status": "PENDING",
        }
        for item in bundle.get("pending_memories", [])
        if isinstance(item, dict)
    ]
    return {"status": status or bundle.get("context_status"), "evidence": evidence, "pending_memories": pending}


def _string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]
