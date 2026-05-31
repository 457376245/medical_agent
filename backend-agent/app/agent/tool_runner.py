"""Agent 工具执行辅助。"""

from __future__ import annotations

import asyncio
from typing import Any

from app.agent.messages import AgentMessage, AgentToolCall
from app.agent.prompting import detect_recent_tool_failures, hash_tool_args
from app.tools.registry import ToolSpec, get_tools


def tool_map(tools: list[ToolSpec] | None = None) -> dict[str, ToolSpec]:
    return {tool.name: tool for tool in (get_tools() if tools is None else tools)}


async def execute_tool_call(
    call: AgentToolCall,
    *,
    tools_by_name: dict[str, ToolSpec],
) -> AgentMessage:
    """执行单个工具调用并返回 tool 消息。"""
    tool = tools_by_name.get(call.name)
    if tool is None:
        content = f"Error: 未知工具 {call.name}"
    else:
        try:
            content = await asyncio.to_thread(tool.handler, **call.args)
        except TypeError as exc:
            content = f"Error: 工具参数错误 — {exc}"
        except Exception as exc:
            content = f"Error: 工具执行失败 — {exc}"
    return AgentMessage(
        role="tool",
        content=str(content),
        name=call.name,
        tool_call_id=call.id,
    )


def split_allowed_tool_calls(
    messages: list[AgentMessage],
    calls: list[AgentToolCall],
) -> tuple[list[AgentToolCall], list[AgentMessage]]:
    """阻断同一轮内相同工具和参数的重复失败调用。"""
    failed_keys = {
        (failure["tool"], failure["args_hash"])
        for failure in detect_recent_tool_failures(messages)
    }
    if not failed_keys:
        return calls, []

    allowed: list[AgentToolCall] = []
    blocked: list[AgentMessage] = []
    for call in calls:
        if (call.name, hash_tool_args(call.args)) not in failed_keys:
            allowed.append(call)
            continue
        blocked.append(
            AgentMessage(
                role="tool",
                content=(
                    "Error: 该工具使用相同参数已失败，"
                    "请向用户说明情况并提供替代建议。"
                ),
                name=call.name,
                tool_call_id=call.id,
            )
        )
    return allowed, blocked
