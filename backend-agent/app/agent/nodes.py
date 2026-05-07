"""图节点实现。

本模块中的每个公共函数都是 LangGraph 状态图中的一个节点：
- ``call_llm``: 将当前消息发送给 LLM
- ``execute_tools``: 执行 LLM 返回的工具调用
"""

from __future__ import annotations

import logging
import os
from typing import Any

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langgraph.prebuilt import ToolNode

try:
    from langchain_openai import ChatOpenAI
except Exception:  # pragma: no cover - 允许在没有可选依赖的情况下本地导入
    ChatOpenAI = None  # type: ignore[assignment]

from app.agent.context import (
    context_signature_from_metadata,
    parse_context_bundle,
)
from app.agent.prompting import (
    build_prompt_messages,
    detect_recent_tool_error_names,
    detect_recent_tool_failures,
    hash_tool_args,
)
from app.config import (
    CONVERSATION_WINDOW_MAX_TOKENS,
    DEFAULT_AGENT_MODEL,
    DEFAULT_AGENT_MAX_TOKENS,
    DEFAULT_AGENT_TEMPERATURE,
    MAX_TOOL_ROUNDS,
    OPENAI_API_KEY,
    OPENAI_BASE_URL,
    OPENAI_REQUEST_TIMEOUT_SECONDS,
    OPENAI_SDK_RETRIES,
)
from app.ids import new_prefixed_ordered_id
from app.tools.registry import get_model_tools, get_tools
from app.utils import normalize_openai_base_url

LOGGER = logging.getLogger(__name__)
CONTEXT_TOOL_NAME = "fetch_disease_profile_context"


def _detect_recent_tool_errors(messages: list[Any]) -> list[str]:
    """扫描最近一轮消息，返回包含错误的工具名列表。"""
    return detect_recent_tool_error_names(messages)


def _context_tool_call_message(metadata: dict[str, Any]) -> AIMessage:
    """构建强制上下文获取的 AI 工具调用消息。"""
    disease_profile_id = str(metadata.get("disease_profile_id") or "").strip()
    record_id = str(metadata.get("record_id") or "").strip()
    patient_id = str(metadata.get("patient_id") or "").strip()
    call_args: dict[str, Any] = {"disease_profile_id": disease_profile_id}
    if record_id:
        call_args["record_id"] = record_id
    if patient_id:
        call_args["patient_id"] = patient_id
    return AIMessage(
        content="",
        tool_calls=[
            {
                "id": new_prefixed_ordered_id("context"),
                "name": CONTEXT_TOOL_NAME,
                "args": call_args,
            }
        ],
    )


def _extract_latest_context_bundle(messages: list[Any]) -> dict[str, Any] | None:
    """查找最新的上下文工具结果并解析其 JSON 内容。"""
    for message in reversed(messages):
        if not isinstance(message, ToolMessage):
            continue
        if str(getattr(message, "name", "")).strip() != CONTEXT_TOOL_NAME:
            continue
        content = message.content
        if isinstance(content, str):
            return parse_context_bundle(content)
        if isinstance(content, list):
            text_parts: list[str] = []
            for item in content:
                if isinstance(item, str):
                    text_parts.append(item)
            if text_parts:
                return parse_context_bundle("".join(text_parts))
    return None


def create_context_preload_node() -> Any:
    """创建决定是否运行上下文工具的节点。"""

    def preload_context(state: dict[str, Any]) -> dict[str, Any]:
        metadata = state.get("metadata", {})
        if not isinstance(metadata, dict):
            metadata = {}

        next_signature = context_signature_from_metadata(metadata)
        active_signature = str(state.get("active_context_signature") or "").strip() or None

        if not next_signature:
            return {
                "active_context_signature": None,
                "active_context_bundle": None,
                "active_context_status": None,
                "pending_context_signature": None,
            }

        if next_signature == active_signature:
            return {"pending_context_signature": None}

        tool_call_message = _context_tool_call_message(metadata)
        return {
            "messages": [tool_call_message],
            "pending_context_signature": next_signature,
        }

    return preload_context


def should_run_preload_tools(state: dict[str, Any]) -> str:
    """路由预加载节点输出到工具节点或直接到 LLM 节点。"""
    messages = state.get("messages", [])
    if not messages:
        return "agent"
    last_message = messages[-1]
    if isinstance(last_message, AIMessage) and last_message.tool_calls:
        return "tools"
    return "agent"


def create_context_sync_node() -> Any:
    """创建将上下文工具输出同步到图状态的节点。"""

    def sync_context(state: dict[str, Any]) -> dict[str, Any]:
        pending_signature = str(state.get("pending_context_signature") or "").strip()
        if not pending_signature:
            return {}

        messages = state.get("messages", [])
        bundle = _extract_latest_context_bundle(messages if isinstance(messages, list) else [])
        if bundle is None:
            return {
                "active_context_signature": pending_signature,
                "active_context_bundle": None,
                "active_context_status": "unavailable",
                "pending_context_signature": None,
            }

        status = str(bundle.get("context_status") or "unavailable").strip().lower()
        normalized_status = status if status in {"ready", "partial", "unavailable"} else "unavailable"
        return {
            "active_context_signature": pending_signature,
            "active_context_bundle": bundle if normalized_status != "unavailable" else None,
            "active_context_status": normalized_status,
            "pending_context_signature": None,
        }

    return sync_context


# ---------------------------------------------------------------------------
# LLM 节点
# ---------------------------------------------------------------------------


def create_llm_node(
    tools: list | None = None,
) -> Any:
    """返回绑定到工具感知 LLM 的 call_llm 函数。

    返回的函数用作图节点。它读取 state["messages"] 并返回一个 AIMessage
    （可能包含工具调用请求）。
    """
    tool_list = tools if tools is not None else get_model_tools()
    if ChatOpenAI is None:
        raise RuntimeError("langchain-openai 未安装")

    api_key = os.getenv("OPENAI_API_KEY", "").strip() or OPENAI_API_KEY
    base_url = normalize_openai_base_url(
        os.getenv("OPENAI_BASE_URL", "").strip() or OPENAI_BASE_URL
    )
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY 未配置")
    if not base_url:
        raise RuntimeError("OPENAI_BASE_URL 未配置")

    llm = ChatOpenAI(
        model=DEFAULT_AGENT_MODEL,
        api_key=api_key,
        base_url=base_url,
        temperature=DEFAULT_AGENT_TEMPERATURE,
        max_tokens=DEFAULT_AGENT_MAX_TOKENS,
        timeout=OPENAI_REQUEST_TIMEOUT_SECONDS,
        max_retries=OPENAI_SDK_RETRIES,
        streaming=True,
    )

    llm_with_tools = llm.bind_tools(tool_list)

    def call_llm(state: dict[str, Any]) -> dict[str, Any]:
        raw_messages = state["messages"]
        prompt_result = build_prompt_messages(
            raw_messages=list(raw_messages),
            state=state,
            max_tokens=CONVERSATION_WINDOW_MAX_TOKENS,
        )
        prepared_messages = prompt_result.messages
        if len(prepared_messages) < prompt_result.diagnostics["prepared_message_count"]:
            LOGGER.info(
                "对话消息已裁剪：%d -> %d 条（预算 %d tokens, prompt_version=%s）",
                prompt_result.diagnostics["prepared_message_count"],
                len(prepared_messages),
                CONVERSATION_WINDOW_MAX_TOKENS,
                prompt_result.diagnostics["prompt_version"],
            )

        response = llm_with_tools.invoke(prepared_messages)

        if not response.tool_calls and not str(response.content or "").strip():
            LOGGER.warning("LLM 返回空内容且无工具调用，尝试重试")
            prepared_messages.append(SystemMessage(content="请根据上述信息为用户提供有价值的回答。"))
            response = llm_with_tools.invoke(prepared_messages)

        return {"messages": [response]}

    return call_llm


# ---------------------------------------------------------------------------
# 工具节点
# ---------------------------------------------------------------------------


def create_tool_node(tools: list | None = None) -> Any:
    """创建用于执行工具调用的 ToolNode。"""
    tool_list = tools or get_tools()
    tool_node = ToolNode(tool_list)

    def execute_tools(state: dict[str, Any]) -> dict[str, Any]:
        messages = state.get("messages", [])
        if not isinstance(messages, list) or not messages:
            return tool_node.invoke(state)
        last_message = messages[-1]
        if not isinstance(last_message, AIMessage) or not last_message.tool_calls:
            return tool_node.invoke(state)

        failed_keys = {
            (failure["tool"], failure["args_hash"])
            for failure in detect_recent_tool_failures(messages[:-1])
        }
        if not failed_keys:
            return tool_node.invoke(state)

        allowed_calls: list[dict[str, Any]] = []
        blocked_messages: list[ToolMessage] = []
        for call in last_message.tool_calls:
            tool_name = str(call.get("name") or "unknown")
            args_hash = hash_tool_args(call.get("args"))
            if (tool_name, args_hash) not in failed_keys:
                allowed_calls.append(call)
                continue
            blocked_messages.append(
                ToolMessage(
                    content=(
                        "Error: 该工具使用相同参数已失败，"
                        "请向用户说明情况并提供替代建议。"
                    ),
                    name=tool_name,
                    tool_call_id=str(call.get("id") or ""),
                )
            )

        if len(allowed_calls) == len(last_message.tool_calls):
            return tool_node.invoke(state)

        updates: dict[str, Any] = {"messages": blocked_messages}
        if allowed_calls:
            allowed_message = AIMessage(
                content=last_message.content,
                tool_calls=allowed_calls,
            )
            allowed_state = {**state, "messages": [*messages[:-1], allowed_message]}
            tool_updates = tool_node.invoke(allowed_state)
            tool_messages = tool_updates.get("messages", []) if isinstance(tool_updates, dict) else []
            updates["messages"] = [*tool_messages, *blocked_messages]
        return updates

    return execute_tools


# ---------------------------------------------------------------------------
# 路由器（条件边）
# ---------------------------------------------------------------------------


def should_continue(state: dict) -> str:
    """决定是路由到工具还是结束对话。"""
    messages = state.get("messages", [])
    if not messages:
        return "end"

    last_message = messages[-1]
    if not isinstance(last_message, AIMessage) or not last_message.tool_calls:
        return "end"

    tool_rounds = 0
    for msg in reversed(messages):
        if isinstance(msg, HumanMessage):
            break
        if isinstance(msg, AIMessage) and msg.tool_calls:
            tool_rounds += 1
    if tool_rounds > MAX_TOOL_ROUNDS:
        LOGGER.warning("工具调用轮数已达上限 (%d)，强制结束", MAX_TOOL_ROUNDS)
        return "end"

    return "tools"
