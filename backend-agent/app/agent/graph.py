"""LangGraph 状态图构建。

构建并编译 Agent 执行图。编译后的图是 api/chat.py 调用的唯一入口点，
处理每次用户交互。
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
    """构建并编译 medical-agent 状态图。

    Args:
        checkpointer: 可选的检查点保存器，用于会话持久化。
                      如果提供，每次带有 thread_id 配置的调用将自动从
                      上次检查点恢复。
        tools: 可选的显式工具列表。默认使用 registry.get_tools()。

    Returns:
        编译后的 CompiledStateGraph，可用于 .invoke() / .astream() /
        .astream_events()。
    """
    call_llm = create_llm_node(tools=tools)
    context_preload_node = create_context_preload_node()
    context_sync_node = create_context_sync_node()
    tool_node = create_tool_node(tools=tools)

    graph = StateGraph(AgentState)

    # 节点
    graph.add_node("context_preload", context_preload_node)
    graph.add_node("context_sync", context_sync_node)
    graph.add_node("agent", call_llm)
    graph.add_node("tools", tool_node)

    # 边
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