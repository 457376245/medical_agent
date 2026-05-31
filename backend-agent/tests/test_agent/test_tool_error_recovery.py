from __future__ import annotations

import asyncio

from app.agent.messages import AgentMessage, AgentToolCall
from app.agent.prompting import detect_recent_tool_error_names
from app.agent.tool_runner import execute_tool_call, split_allowed_tool_calls
from app.tools.registry import ToolSpec


def test_detect_recent_tool_errors_finds_error() -> None:
    messages = [
        AgentMessage(role="user", content="解析这份报告"),
        AgentMessage(
            role="assistant",
            tool_calls=[AgentToolCall(id="tc1", name="parse_document", args={})],
        ),
        AgentMessage(role="tool", content="Error: 文档解析失败", name="parse_document", tool_call_id="tc1"),
    ]
    assert detect_recent_tool_error_names(messages) == ["parse_document"]


def test_detect_recent_tool_errors_ignores_success() -> None:
    messages = [
        AgentMessage(role="user", content="你好"),
        AgentMessage(
            role="assistant",
            tool_calls=[AgentToolCall(id="tc1", name="parse_document", args={})],
        ),
        AgentMessage(role="tool", content="ALT: 85 U/L", name="parse_document", tool_call_id="tc1"),
    ]
    assert detect_recent_tool_error_names(messages) == []


def test_detect_recent_tool_errors_stops_at_user_message() -> None:
    messages = [
        AgentMessage(role="user", content="第一轮"),
        AgentMessage(
            role="assistant",
            tool_calls=[AgentToolCall(id="tc1", name="parse_document", args={})],
        ),
        AgentMessage(role="tool", content="Error: 旧错误", name="parse_document", tool_call_id="tc1"),
        AgentMessage(role="assistant", content="已知晓"),
        AgentMessage(role="user", content="第二轮"),
        AgentMessage(role="assistant", content="好的"),
    ]
    assert detect_recent_tool_error_names(messages) == []


def test_detect_recent_tool_errors_multiple_errors() -> None:
    messages = [
        AgentMessage(role="user", content="同时解析"),
        AgentMessage(
            role="assistant",
            tool_calls=[
                AgentToolCall(id="tc1", name="parse_document", args={}),
                AgentToolCall(id="tc2", name="generate_medical_text", args={}),
            ],
        ),
        AgentMessage(role="tool", content="Error: 解析失败", name="parse_document", tool_call_id="tc1"),
        AgentMessage(role="tool", content="Error: 生成失败", name="generate_medical_text", tool_call_id="tc2"),
    ]
    errors = detect_recent_tool_error_names(messages)
    assert len(errors) == 2
    assert "parse_document" in errors
    assert "generate_medical_text" in errors


def test_tool_runner_short_circuits_repeated_failed_args() -> None:
    messages = [
        AgentMessage(role="user", content="重试"),
        AgentMessage(
            role="assistant",
            tool_calls=[AgentToolCall(id="first", name="retry_me", args={"value": "same"})],
        ),
        AgentMessage(role="tool", content="Error: 第一次失败", name="retry_me", tool_call_id="first"),
    ]
    allowed, blocked = split_allowed_tool_calls(
        messages,
        [AgentToolCall(id="second", name="retry_me", args={"value": "same"})],
    )

    assert allowed == []
    assert blocked[0].content.startswith("Error:")
    assert blocked[0].tool_call_id == "second"


def test_execute_tool_call_invokes_handler() -> None:
    calls = {"count": 0}

    def retry_me(value: str) -> str:
        calls["count"] += 1
        return value

    tool_message = asyncio.run(
        execute_tool_call(
            AgentToolCall(id="tc1", name="retry_me", args={"value": "ok"}),
            tools_by_name={
                "retry_me": ToolSpec(
                    name="retry_me",
                    description="测试工具",
                    parameters={},
                    handler=retry_me,
                )
            },
        )
    )

    assert calls["count"] == 1
    assert tool_message.content == "ok"
    assert tool_message.tool_call_id == "tc1"
