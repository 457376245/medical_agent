from __future__ import annotations

from typing import Any

from app.agent.messages import AgentMessage
from app.agent.prompting import _trim_messages


def test_short_conversation_not_trimmed() -> None:
    messages = [
        AgentMessage(role="user", content="你好"),
        AgentMessage(role="assistant", content="你好，有什么可以帮助你的？"),
    ]
    trimmed = _trim_messages(messages=messages, max_tokens=100_000)
    assert len(trimmed) == len(messages)


def test_long_conversation_trimmed_to_budget() -> None:
    messages: list[Any] = []
    for i in range(100):
        messages.append(AgentMessage(role="user", content=f"用户消息 {i}" * 50))
        messages.append(AgentMessage(role="assistant", content=f"助手回复 {i}" * 50))

    trimmed = _trim_messages(messages=messages, max_tokens=500)
    assert len(trimmed) < len(messages)


def test_system_messages_preserved_after_trimming() -> None:
    messages: list[Any] = [AgentMessage(role="system", content="系统提示")]
    for i in range(100):
        messages.append(AgentMessage(role="user", content=f"用户消息 {i}" * 50))
        messages.append(AgentMessage(role="assistant", content=f"助手回复 {i}" * 50))

    trimmed = _trim_messages(messages=messages, max_tokens=500)
    assert len(trimmed) < len(messages)
    assert trimmed[0].role == "system"
    assert trimmed[0].content == "系统提示"


def test_trimmed_messages_start_with_user_after_system() -> None:
    messages: list[Any] = []
    for i in range(50):
        messages.append(AgentMessage(role="user", content=f"用户消息 {i}" * 50))
        messages.append(AgentMessage(role="assistant", content=f"助手回复 {i}" * 50))

    trimmed = _trim_messages(messages=messages, max_tokens=500)
    first_non_system = next(m for m in trimmed if m.role != "system")
    assert first_non_system.role == "user"
