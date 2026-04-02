from __future__ import annotations

from typing import Any

from langchain_core.messages import AIMessage, HumanMessage

from app.agent import nodes as nodes_module


def test_create_llm_node_uses_chat_openai(monkeypatch: Any) -> None:
    captured_kwargs: dict[str, Any] = {}

    class DummyChatOpenAI:
        def __init__(self, **kwargs: Any) -> None:
            captured_kwargs.update(kwargs)

        def bind_tools(self, tools: list[Any]) -> "DummyChatOpenAI":
            self._tools = tools
            return self

        def invoke(self, messages: list[Any]) -> AIMessage:
            assert messages[0].content
            return AIMessage(content="ok")

    monkeypatch.setattr(nodes_module, "ChatOpenAI", DummyChatOpenAI)
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    monkeypatch.setenv("OPENAI_BASE_URL", "http://127.0.0.1:8317")

    call_llm = nodes_module.create_llm_node(tools=[])
    result = call_llm({"messages": [HumanMessage(content="你好")]})

    assert captured_kwargs["model"]
    assert captured_kwargs["base_url"] == "http://127.0.0.1:8317/v1"
    assert "messages" in result
    assert result["messages"][0].content == "ok"
