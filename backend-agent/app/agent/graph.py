"""LangGraph state-graph construction.

Builds and compiles the Agent execution graph.  The compiled graph is the
single entry-point that ``api/chat.py`` invokes for every user turn.
"""

from __future__ import annotations

import logging
from typing import Any

from langgraph.graph import END, StateGraph
from langgraph.checkpoint.base import BaseCheckpointSaver

from app.agent.nodes import (
    create_context_preload_node,
    create_context_sync_node,
    create_llm_node,
    create_tool_node,
    should_continue,
    should_run_preload_tools,
)
from app.agent.state import AgentState

LOGGER = logging.getLogger(__name__)


def build_graph(
    *,
    checkpointer: BaseCheckpointSaver | None = None,
    tools: list | None = None,
) -> Any:
    """Build and compile the medical-agent state graph.

    Args:
        checkpointer: Optional checkpoint saver for session persistence.
                      When provided, each invocation with a ``thread_id``
                      config will automatically resume from the last
                      checkpoint.
        tools: Optional explicit tool list.  Defaults to
               ``registry.get_tools()``.

    Returns:
        A compiled ``CompiledStateGraph`` ready for ``.invoke()`` /
        ``.astream()`` / ``.astream_events()``.
    """
    call_llm = create_llm_node(tools=tools)
    context_preload_node = create_context_preload_node()
    context_sync_node = create_context_sync_node()
    tool_node = create_tool_node(tools=tools)

    graph = StateGraph(AgentState)

    # Nodes
    graph.add_node("context_preload", context_preload_node)
    graph.add_node("context_sync", context_sync_node)
    graph.add_node("agent", call_llm)
    graph.add_node("tools", tool_node)

    # Edges
    graph.set_entry_point("context_preload")
    graph.add_conditional_edges(
        "context_preload",
        should_run_preload_tools,
        {
            "tools": "tools",
            "agent": "agent",
        },
    )
    graph.add_conditional_edges(
        "agent",
        should_continue,
        {
            "tools": "tools",
            "end": END,
        },
    )
    graph.add_edge("tools", "context_sync")
    graph.add_edge("context_sync", "agent")

    compiled = graph.compile(checkpointer=checkpointer)
    LOGGER.info("Agent graph compiled (checkpointer=%s)", type(checkpointer).__name__)
    return compiled
