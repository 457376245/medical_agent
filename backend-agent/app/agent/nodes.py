"""Graph node implementations.

Each public function in this module is a node in the LangGraph state-graph:
- ``call_llm``: sends the current messages to the LLM
- ``execute_tools``: dispatches tool calls returned by the LLM
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
except Exception:  # pragma: no cover - allows local import without optional deps
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
    """Build an AI tool-call message that forces context fetch."""
    disease_profile_id = str(metadata.get("disease_profile_id") or "").strip()
    record_id = str(metadata.get("record_id") or "").strip()
    call_args: dict[str, Any] = {"disease_profile_id": disease_profile_id}
    if record_id:
        call_args["record_id"] = record_id
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
    """Find the latest context-tool result and parse its JSON content."""
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
    """Create node that decides whether context tool must run."""

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
    """Route preload node output either to tools or directly to LLM node."""
    messages = state.get("messages", [])
    if not messages:
        return "agent"
    last_message = messages[-1]
    if isinstance(last_message, AIMessage) and last_message.tool_calls:
        return "tools"
    return "agent"


def create_context_sync_node() -> Any:
    """Create node that syncs context tool output into graph state."""

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
# LLM node
# ---------------------------------------------------------------------------


def create_llm_node(
    tools: list | None = None,
) -> Any:
    """Return an ``call_llm`` function bound to a tool-aware LLM.

    The returned function is used as a graph node.  It reads
    ``state["messages"]`` and returns an ``AIMessage`` (possibly with
    tool-call requests).
    """
    tool_list = tools or get_tools()
    if ChatOpenAI is None:
        raise RuntimeError("langchain-openai is not installed")

    api_key = os.getenv("OPENAI_API_KEY", "").strip() or OPENAI_API_KEY
    base_url = normalize_openai_base_url(
        os.getenv("OPENAI_BASE_URL", "").strip() or OPENAI_BASE_URL
    )
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY is not configured")
    if not base_url:
        raise RuntimeError("OPENAI_BASE_URL is not configured")

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
        """Invoke the LLM with the current message history."""
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
# Tool node
# ---------------------------------------------------------------------------


def create_tool_node(tools: list | None = None) -> ToolNode:
    """Create a ``ToolNode`` that dispatches tool calls."""
    tool_list = tools or get_tools()
    return ToolNode(tool_list)


# ---------------------------------------------------------------------------
# Router (conditional edge)
# ---------------------------------------------------------------------------


def should_continue(state: dict) -> str:
    """Decide whether to route to tools or end the conversation.

    Returns:
        ``"tools"`` if the last AI message contains tool calls,
        ``"end"`` otherwise.
    """
    messages = state.get("messages", [])
    if not messages:
        return "end"

    last_message = messages[-1]
    if isinstance(last_message, AIMessage) and last_message.tool_calls:
        return "tools"
    return "end"
