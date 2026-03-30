"""Graph node implementations.

Each public function in this module is a node in the LangGraph state-graph:
- ``call_llm``: sends the current messages to the LLM
- ``execute_tools``: dispatches tool calls returned by the LLM
"""

from __future__ import annotations

import logging
from typing import Any

from langchain_core.messages import AIMessage
from langchain_google_genai import ChatGoogleGenerativeAI
from langgraph.prebuilt import ToolNode

from app.config import (
    DEFAULT_AGENT_MODEL,
    DEFAULT_AGENT_TEMPERATURE,
    DEFAULT_AGENT_MAX_TOKENS,
)
from app.prompts.system import SYSTEM_MEDICAL_ASSISTANT
from app.tools.registry import get_tools

LOGGER = logging.getLogger(__name__)

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
    import os

    tool_list = tools or get_tools()

    api_key = (
        os.getenv("GOOGLE_API_KEY", "").strip()
        or os.getenv("GEMINI_API_KEY", "").strip()
    )

    llm = ChatGoogleGenerativeAI(
        model=DEFAULT_AGENT_MODEL,
        google_api_key=api_key or "not-set",
        temperature=DEFAULT_AGENT_TEMPERATURE,
        max_output_tokens=DEFAULT_AGENT_MAX_TOKENS,
    )

    llm_with_tools = llm.bind_tools(tool_list)

    def call_llm(state: dict) -> dict:
        """Invoke the LLM with the current message history."""
        messages = state["messages"]

        # Prepend system prompt if not already present
        from langchain_core.messages import SystemMessage

        if not messages or not isinstance(messages[0], SystemMessage):
            messages = [SystemMessage(content=SYSTEM_MEDICAL_ASSISTANT)] + list(
                messages
            )

        response = llm_with_tools.invoke(messages)
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
