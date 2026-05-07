"""Agent 运行时 prompt 组装。"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from typing import Any

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage, trim_messages

from app.agent.context import build_context_system_message
from app.prompts.system import SYSTEM_MEDICAL_ASSISTANT
from app.prompts.templates import get_conversation_prompt

AGENT_PROMPT_VERSION = "agent-prompt-v1"
_TOOL_ERROR_PREFIX = "Error:"


@dataclass(frozen=True)
class PromptBuildResult:
    """最终发送给模型的消息和可观测诊断信息。"""

    messages: list[Any]
    diagnostics: dict[str, Any]


def detect_recent_tool_failures(messages: list[Any]) -> list[dict[str, str]]:
    """扫描最近一轮消息，返回工具错误及其参数摘要。"""
    calls_by_id: dict[str, tuple[str, str]] = {}
    for msg in messages:
        if not isinstance(msg, AIMessage):
            continue
        for call in msg.tool_calls or []:
            call_id = str(call.get("id") or "").strip()
            tool_name = str(call.get("name") or "unknown").strip() or "unknown"
            args_hash = hash_tool_args(call.get("args"))
            if call_id:
                calls_by_id[call_id] = (tool_name, args_hash)

    failures: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for msg in reversed(messages):
        if isinstance(msg, HumanMessage):
            break
        if not isinstance(msg, ToolMessage):
            continue
        content = str(msg.content or "").strip()
        if not content.startswith(_TOOL_ERROR_PREFIX):
            continue
        call_id = str(getattr(msg, "tool_call_id", "") or "")
        fallback_name = str(getattr(msg, "name", "unknown") or "unknown")
        tool_name, args_hash = calls_by_id.get(call_id, (fallback_name, ""))
        key = (tool_name, args_hash)
        if key in seen:
            continue
        seen.add(key)
        failures.append({"tool": tool_name, "args_hash": args_hash})
    return failures


def detect_recent_tool_error_names(messages: list[Any]) -> list[str]:
    """兼容旧测试和调用方：只返回最近一轮失败工具名。"""
    names: list[str] = []
    for failure in detect_recent_tool_failures(messages):
        name = failure["tool"]
        if name not in names:
            names.append(name)
    return names


def build_prompt_messages(
    *,
    raw_messages: list[Any],
    state: dict[str, Any],
    max_tokens: int,
) -> PromptBuildResult:
    """根据图状态构造最终 LLM 输入消息。"""
    metadata = state.get("metadata") or {}
    if not isinstance(metadata, dict):
        metadata = {}

    parts: list[str] = [SYSTEM_MEDICAL_ASSISTANT]

    attachment_hint = _build_attachment_hint(metadata.get("attachments"))
    if attachment_hint:
        parts.append(attachment_hint)

    context_message = build_context_system_message(
        active_context_bundle=state.get("active_context_bundle"),
        active_context_status=str(state.get("active_context_status") or "").strip() or None,
    )
    if context_message:
        parts.append(context_message)

    scenario_prompt = get_conversation_prompt(
        workflow=metadata.get("workflow"),
        scenario=metadata.get("scenario"),
        audience=metadata.get("audience"),
        urgency_level=metadata.get("urgency_level"),
    )
    if scenario_prompt:
        parts.append(scenario_prompt)

    recent_failures = detect_recent_tool_failures(raw_messages)
    if recent_failures:
        names = "、".join(_unique(failure["tool"] for failure in recent_failures))
        parts.append(
            f"[注意] 以下工具调用返回了错误：{names}。"
            "请勿使用相同工具和参数重试，向用户说明情况并提供替代建议。"
        )

    system_message = SystemMessage(content="\n\n".join(parts))
    conversation_messages = [
        message for message in raw_messages if not isinstance(message, SystemMessage)
    ]
    prepared = [system_message, *conversation_messages]
    trimmed = trim_messages(
        prepared,
        max_tokens=max_tokens,
        token_counter="approximate",
        strategy="last",
        include_system=True,
        start_on="human",
    )

    diagnostics = {
        "prompt_version": AGENT_PROMPT_VERSION,
        "workflow": metadata.get("workflow"),
        "scenario": metadata.get("scenario"),
        "context_status": state.get("active_context_status"),
        "attachments_included": bool(attachment_hint),
        "tool_failures": recent_failures,
        "raw_message_count": len(raw_messages),
        "prepared_message_count": len(prepared),
        "final_message_count": len(trimmed),
    }
    return PromptBuildResult(messages=list(trimmed), diagnostics=diagnostics)


def _build_attachment_hint(value: Any) -> str | None:
    if not isinstance(value, list) or not value:
        return None
    lines: list[str] = []
    for idx, item in enumerate(value[:5], start=1):
        if not isinstance(item, dict):
            continue
        object_key = str(item.get("object_key") or "").strip()
        if not object_key:
            continue
        file_type = str(item.get("file_type") or "PDF").strip().upper() or "PDF"
        display_name = str(item.get("display_name") or f"附件{idx}").strip()
        lines.append(
            f"- {display_name}：file_type={file_type}，object_key={object_key}"
        )
    if not lines:
        return None
    return (
        "【当前可用附件】\n"
        "用户本轮提供了以下附件。仅当用户明确要求读取、分析或解读附件内容时，"
        "才调用 parse_document，并使用对应 object_key 与 file_type；普通问答不要解析附件。\n"
        + "\n".join(lines)
    )


def hash_tool_args(value: Any) -> str:
    """生成稳定的工具参数摘要，用于识别重复失败调用。"""
    try:
        rendered = json.dumps(value or {}, ensure_ascii=False, sort_keys=True)
    except TypeError:
        rendered = str(value or {})
    return hashlib.sha256(rendered.encode("utf-8")).hexdigest()[:12]


def _unique(values: Any) -> list[str]:
    result: list[str] = []
    for value in values:
        rendered = str(value).strip()
        if rendered and rendered not in result:
            result.append(rendered)
    return result
