from __future__ import annotations

from typing import Any
from unittest.mock import MagicMock

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, trim_messages

from app.agent.context import build_context_system_message


def test_short_conversation_not_trimmed() -> None:
    messages = [
        HumanMessage(content="你好"),
        AIMessage(content="你好，有什么可以帮助你的？"),
    ]
    trimmed = trim_messages(
        messages,
        max_tokens=100_000,
        token_counter="approximate",
        strategy="last",
        include_system=True,
        start_on="human",
    )
    assert len(trimmed) == len(messages)


def test_long_conversation_trimmed_to_budget() -> None:
    messages: list[Any] = []
    for i in range(100):
        messages.append(HumanMessage(content=f"用户消息 {i}" * 50))
        messages.append(AIMessage(content=f"助手回复 {i}" * 50))

    trimmed = trim_messages(
        messages,
        max_tokens=500,
        token_counter="approximate",
        strategy="last",
        include_system=True,
        start_on="human",
    )
    assert len(trimmed) < len(messages)


def test_system_messages_preserved_after_trimming() -> None:
    messages: list[Any] = [SystemMessage(content="系统提示")]
    for i in range(100):
        messages.append(HumanMessage(content=f"用户消息 {i}" * 50))
        messages.append(AIMessage(content=f"助手回复 {i}" * 50))

    trimmed = trim_messages(
        messages,
        max_tokens=500,
        token_counter="approximate",
        strategy="last",
        include_system=True,
        start_on="human",
    )
    assert len(trimmed) < len(messages)
    assert isinstance(trimmed[0], SystemMessage)
    assert trimmed[0].content == "系统提示"


def test_trimmed_messages_start_with_human_after_system() -> None:
    messages: list[Any] = []
    for i in range(50):
        messages.append(HumanMessage(content=f"用户消息 {i}" * 50))
        messages.append(AIMessage(content=f"助手回复 {i}" * 50))

    trimmed = trim_messages(
        messages,
        max_tokens=500,
        token_counter="approximate",
        strategy="last",
        include_system=True,
        start_on="human",
    )
    first_non_system = next(m for m in trimmed if not isinstance(m, SystemMessage))
    assert isinstance(first_non_system, HumanMessage)
