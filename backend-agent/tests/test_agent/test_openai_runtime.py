from __future__ import annotations

from typing import Any

from app.agent.runtime import AgentRuntime


class _Delta:
    def __init__(self, *, content: str | None = None, tool_calls: list[Any] | None = None) -> None:
        self.content = content
        self.tool_calls = tool_calls


class _Choice:
    def __init__(self, delta: _Delta) -> None:
        self.delta = delta


class _Chunk:
    def __init__(self, delta: _Delta) -> None:
        self.choices = [_Choice(delta)]


class _Response:
    def __init__(self, chunks: list[_Chunk]) -> None:
        self._chunks = chunks

    def __aiter__(self) -> "_Response":
        return self

    async def __anext__(self) -> _Chunk:
        if not self._chunks:
            raise StopAsyncIteration
        return self._chunks.pop(0)


class _Completions:
    def __init__(self) -> None:
        self.kwargs: dict[str, Any] = {}

    async def create(self, **kwargs: Any) -> _Response:
        self.kwargs = kwargs
        return _Response([_Chunk(_Delta(content="ok"))])


class _Chat:
    def __init__(self) -> None:
        self.completions = _Completions()


class _Client:
    def __init__(self) -> None:
        self.chat = _Chat()


async def _collect(runtime: AgentRuntime) -> list[str]:
    result: list[str] = []
    async for event in runtime.stream(
        thread_id="thread-1",
        user_message="你好",
        metadata={},
    ):
        if event.type == "token":
            result.append(event.content)
    return result


def test_agent_runtime_streams_openai_tokens() -> None:
    import asyncio

    client = _Client()
    runtime = AgentRuntime(client=client, model_tools=[], all_tools=[])  # type: ignore[arg-type]

    tokens = asyncio.run(_collect(runtime))

    assert tokens == ["ok"]
    assert client.chat.completions.kwargs["model"]
    assert client.chat.completions.kwargs["messages"][0]["role"] == "system"
