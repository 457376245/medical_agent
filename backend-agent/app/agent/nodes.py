"""图节点实现。

本模块中的每个公共函数都是 LangGraph 状态图中的一个节点：
- ``call_llm``: 将当前消息发送给 LLM
- ``execute_tools``: 执行 LLM 返回的工具调用
"""

from __future__ import annotations

import logging
import os
import uuid
from typing import Any

from langchain_core.messages import AIMessage, SystemMessage, ToolMessage
from langgraph.prebuilt import ToolNode

try:
    from langchain_openai import ChatOpenAI
except Exception:  # pragma: no cover - 允许在没有可选依赖的情况下本地导入
    ChatOpenAI = None  # type: ignore[assignment]

from app.agent.context import (
    build_context_system_message,
    context_signature_from_metadata,
    parse_context_bundle,
)
from app.config import (
    DEFAULT_AGENT_MODEL,
    DEFAULT_AGENT_MAX_TOKENS,
    DEFAULT_AGENT_TEMPERATURE,
    OPENAI_API_KEY,
    OPENAI_BASE_URL,
    OPENAI_REQUEST_TIMEOUT_SECONDS,
    OPENAI_SDK_RETRIES,
)
from app.prompts.system import SYSTEM_MEDICAL_ASSISTANT
from app.tools.registry import get_tools
from app.utils import normalize_openai_base_url

LOGGER = logging.getLogger(__name__)
CONTEXT_TOOL_NAME = "fetch_disease_profile_context"


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
                "id": f"context-{uuid.uuid4().hex[:12]}",
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
    tool_list = tools or get_tools()
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
        """使用当前消息历史调用 LLM。"""
        messages = state["messages"]
        prepared_messages = list(messages)
        if not prepared_messages or not isinstance(prepared_messages[0], SystemMessage):
            prepared_messages = [SystemMessage(content=SYSTEM_MEDICAL_ASSISTANT)] + prepared_messages

        context_message = build_context_system_message(
            active_context_bundle=state.get("active_context_bundle"),
            active_context_status=str(state.get("active_context_status") or "").strip() or None,
        )
        if context_message:
            prepared_messages = [
                prepared_messages[0],
                SystemMessage(content=context_message),
                *prepared_messages[1:],
            ]

        response = llm_with_tools.invoke(prepared_messages)
        return {"messages": [response]}

    return call_llm


# ---------------------------------------------------------------------------
# 工具节点
# ---------------------------------------------------------------------------


def create_tool_node(tools: list | None = None) -> ToolNode:
    """创建用于执行工具调用的 ToolNode。"""
    tool_list = tools or get_tools()
    return ToolNode(tool_list)


# ---------------------------------------------------------------------------
# 路由器（条件边）
# ---------------------------------------------------------------------------


def should_continue(state: dict) -> str:
    """决定是路由到工具还是结束对话。

    Returns:
        如果最后的 AI 消息包含工具调用则返回 "tools"，
        否则返回 "end"。
    """
    messages = state.get("messages", [])
    if not messages:
        return "end"

    last_message = messages[-1]
    if isinstance(last_message, AIMessage) and last_message.tool_calls:
        return "tools"
    return "end"