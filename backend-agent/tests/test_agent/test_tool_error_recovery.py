from __future__ import annotations

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langchain_core.tools import tool

from app.agent.nodes import _detect_recent_tool_errors, create_tool_node


def test_detect_recent_tool_errors_finds_error() -> None:
    messages = [
        HumanMessage(content="解析这份报告"),
        AIMessage(content="", tool_calls=[{"id": "tc1", "name": "parse_document", "args": {}}]),
        ToolMessage(content="Error: 文档解析失败 — BIZ_EMPTY_CONTENT", name="parse_document", tool_call_id="tc1"),
    ]
    errors = _detect_recent_tool_errors(messages)
    assert errors == ["parse_document"]


def test_detect_recent_tool_errors_ignores_success() -> None:
    messages = [
        HumanMessage(content="你好"),
        AIMessage(content="", tool_calls=[{"id": "tc1", "name": "parse_document", "args": {}}]),
        ToolMessage(content="ALT: 85 U/L (0-40)", name="parse_document", tool_call_id="tc1"),
    ]
    errors = _detect_recent_tool_errors(messages)
    assert errors == []


def test_detect_recent_tool_errors_stops_at_human_message() -> None:
    messages = [
        HumanMessage(content="第一轮"),
        AIMessage(content="", tool_calls=[{"id": "tc1", "name": "parse_document", "args": {}}]),
        ToolMessage(content="Error: 旧错误", name="parse_document", tool_call_id="tc1"),
        AIMessage(content="已知晓"),
        HumanMessage(content="第二轮"),
        AIMessage(content="好的"),
    ]
    errors = _detect_recent_tool_errors(messages)
    assert errors == []


def test_detect_recent_tool_errors_multiple_errors() -> None:
    messages = [
        HumanMessage(content="同时解析"),
        AIMessage(content="", tool_calls=[
            {"id": "tc1", "name": "parse_document", "args": {}},
            {"id": "tc2", "name": "generate_medical_text", "args": {}},
        ]),
        ToolMessage(content="Error: 解析失败", name="parse_document", tool_call_id="tc1"),
        ToolMessage(content="Error: 生成失败", name="generate_medical_text", tool_call_id="tc2"),
    ]
    errors = _detect_recent_tool_errors(messages)
    assert len(errors) == 2
    assert "parse_document" in errors
    assert "generate_medical_text" in errors


def test_tool_node_short_circuits_repeated_failed_args() -> None:
    calls = {"count": 0}

    @tool
    def retry_me(value: str) -> str:
        """测试工具。"""
        calls["count"] += 1
        return value

    node = create_tool_node(tools=[retry_me])
    messages = [
        HumanMessage(content="重试"),
        AIMessage(
            content="",
            tool_calls=[{"id": "first", "name": "retry_me", "args": {"value": "same"}}],
        ),
        ToolMessage(content="Error: 第一次失败", name="retry_me", tool_call_id="first"),
        AIMessage(
            content="",
            tool_calls=[{"id": "second", "name": "retry_me", "args": {"value": "same"}}],
        ),
    ]

    result = node({"messages": messages})

    assert calls["count"] == 0
    assert result["messages"][0].content.startswith("Error:")
    assert result["messages"][0].tool_call_id == "second"
