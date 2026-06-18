from __future__ import annotations

from types import SimpleNamespace
from typing import Any

from app.agent.runtime import AgentRuntime


class _Session:
    def __init__(self) -> None:
        self.closed = False

    async def get_items(self) -> list[dict[str, Any]]:
        return []

    async def clear_session(self) -> None:
        return None

    async def close(self) -> None:
        self.closed = True


class _RunResult:
    run_loop_exception = None

    async def stream_events(self):  # noqa: ANN201
        yield SimpleNamespace(
            type="raw_response_event",
            data=SimpleNamespace(type="response.output_text.delta", delta="ok"),
        )


class _Runner:
    kwargs: dict[str, Any] = {}

    @classmethod
    def run_streamed(cls, *args: Any, **kwargs: Any) -> _RunResult:
        cls.kwargs = {"args": args, **kwargs}
        return _RunResult()


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


def test_agent_runtime_streams_agents_sdk_tokens() -> None:
    import asyncio

    session = _Session()
    runtime = AgentRuntime(
        runner=_Runner,
        session_factory=lambda _thread_id: session,
        model_tools=[],
        all_tools=[],
    )

    tokens = asyncio.run(_collect(runtime))

    agent = _Runner.kwargs["args"][0]
    assert tokens == ["ok"]
    assert agent.name == "medical-agent"
    assert agent.model
    assert _Runner.kwargs["input"] == "你好"
    assert _Runner.kwargs["session"] is session
    assert session.closed is True
