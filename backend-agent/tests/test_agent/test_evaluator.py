from __future__ import annotations

import asyncio
import json
from types import SimpleNamespace
from typing import Any

from app.agent import evaluator as evaluator_module
from app.agent.evaluator import EvaluationAgentOutput, evaluate_answer, unavailable_evaluation


def test_evaluate_answer_returns_normalized_payload():
    async def invoke_json(messages):
        del messages
        return json.dumps({"overall_score": 88, "risk_level": "medium", "summary": "ok", "issues": [{"severity": "low", "message": "issue"}], "suggestions": ["tip"]})

    result = asyncio.run(evaluate_answer(
        user_message="q",
        assistant_answer="a [E-1]",
        metadata={"disease_name": "dm"},
        grounded_context={"status": "ready", "evidence": [{"evidence_id": "E-1", "summary": "ALT=85"}]},
        invoke_json=invoke_json,
    ))
    assert result["status"] == "available"
    assert result["overall_score"] == 88


def test_evaluate_answer_failure_returns_unavailable():
    async def invoke_json(messages):
        del messages
        raise RuntimeError("boom")

    result = asyncio.run(evaluate_answer(user_message="hi", assistant_answer="hello", invoke_json=invoke_json))
    assert result["status"] == "unavailable"
    assert result["rubric_version"] == "grounded-evaluator-v2"


def test_evaluate_answer_uses_agents_sdk_runner_when_no_invoke_json():
    class _StubRunner:
        last_kwargs: dict[str, Any] = {}

        @classmethod
        async def run(cls, agent, input, **kwargs):
            cls.last_kwargs = {"agent": agent, "input": input, **kwargs}
            payload = EvaluationAgentOutput(overall_score=77, risk_level="low", summary="sdk ok", issues=[], suggestions=[])
            return SimpleNamespace(final_output_as=lambda _cls, raise_if_incorrect_type=False: payload)

    result = asyncio.run(evaluate_answer(user_message="q", assistant_answer="answer body", metadata={"workflow": "report_interpretation"}, runner=_StubRunner))
    assert result["status"] == "available"
    assert result["overall_score"] == 77
    assert _StubRunner.last_kwargs["agent"].name == "answer-evaluator"
    assert _StubRunner.last_kwargs["agent"].tools == []
    assert _StubRunner.last_kwargs["max_turns"] == 1


def test_evaluator_receives_grounded_evidence_and_times_out(monkeypatch):
    captured = {}

    async def capture(messages):
        captured["messages"] = messages
        return json.dumps({"overall_score": 90, "risk_level": "low", "summary": "ok"})

    result = asyncio.run(evaluate_answer(
        user_message="q",
        assistant_answer="a",
        grounded_context={"status": "ready", "evidence": [{"evidence_id": "E-ALT", "summary": "ALT=85"}]},
        invoke_json=capture,
    ))
    assert result["status"] == "available"
    assert "E-ALT" in captured["messages"][1]["content"]

    monkeypatch.setattr(evaluator_module, "ANSWER_EVALUATOR_TIMEOUT_SECONDS", 0.01)

    async def slow(_messages):
        await asyncio.sleep(0.1)
        return "{}"

    timeout_result = asyncio.run(evaluate_answer(user_message="q", assistant_answer="a", invoke_json=slow))
    assert timeout_result["status"] == "unavailable"
