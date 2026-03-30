"""LangGraph state-graph construction.

Builds and compiles the Agent execution graph.  The compiled graph is the
single entry-point that ``api/chat.py`` invokes for every user turn.
"""

from __future__ import annotations

import logging
from typing import Any

from langgraph.graph import END, StateGraph
from langgraph.checkpoint.base import BaseCheckpointSaver

from app.agent.nodes import create_llm_node, create_tool_node, should_continue
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
    tool_node = create_tool_node(tools=tools)

    graph = StateGraph(AgentState)

    # Nodes
    graph.add_node("agent", call_llm)
    graph.add_node("tools", tool_node)

    # Edges
    graph.set_entry_point("agent")
    graph.add_conditional_edges(
        "agent",
        should_continue,
        {
            "tools": "tools",
            "end": END,
        },
    )
    graph.add_edge("tools", "agent")

    compiled = graph.compile(checkpointer=checkpointer)
    LOGGER.info("Agent graph compiled (checkpointer=%s)", type(checkpointer).__name__)
    return compiled
