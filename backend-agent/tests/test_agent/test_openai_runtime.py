from __future__ import annotations

from types import SimpleNamespace
from typing import Any

from app.agent.runtime import AgentRuntime
from app.auth import AgentScope


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


class _StateStore:
    state = None

    async def get_agent_runtime_state(self, thread_id, owner_key):
        return self.state

    async def upsert_agent_runtime_state(self, state):
        self.state = state


async def _collect(runtime: AgentRuntime) -> list[str]:
    result: list[str] = []
    async for event in runtime.stream(
        thread_id="thread-1",
        user_message="你好",
        metadata={},
        scope=AgentScope(tenant_id="tenant-1", user_id="user-1", patient_id="patient-1"),
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
    run_config = _Runner.kwargs["run_config"]
    assert run_config.session_settings.limit == 40
    assert run_config.trace_include_sensitive_data is False
    assert run_config.workflow_name == "medical-agent-chat"
    assert session.closed is True


def test_history_callback_drops_old_tools_and_applies_real_budget() -> None:
    import asyncio

    runtime = AgentRuntime(
        runner=_Runner,
        session_factory=lambda _thread_id: _Session(),
        model_tools=[],
        all_tools=[],
    )
    asyncio.run(_collect(runtime))
    callback = _Runner.kwargs["run_config"].session_input_callback
    history = []
    for index in range(60):
        history.extend([
            {"role": "user", "content": f"question-{index}-" + "x" * 400},
            {"type": "function_call", "name": "parse_document", "arguments": "x" * 2000},
            {"type": "function_call_output", "output": "secret" * 500},
            {"role": "assistant", "content": f"answer-{index}-" + "y" * 400},
        ])
    current = [{"role": "user", "content": "current question"}]

    selected = callback(history, current)

    assert selected[-1] == current[0]
    assert len(selected) < len(history)
    assert not any("function_call" in str(item.get("type")) for item in selected[:-1])


def test_runtime_persists_only_safe_context_diagnostics() -> None:
    import asyncio

    store = _StateStore()
    runtime = AgentRuntime(
        state_store=store,
        runner=_Runner,
        session_factory=lambda _thread_id: _Session(),
        model_tools=[],
        all_tools=[],
    )
    asyncio.run(_collect(runtime))

    diagnostics = store.state.last_diagnostics
    assert diagnostics["model"]
    assert diagnostics["prompt_version"] == "agent-prompt-v1"
    assert diagnostics["context_version"] == "context-v2"
    assert diagnostics["evaluator_version"] == "grounded-evaluator-v2"
    assert set(diagnostics["usage"]) == {"requests", "input_tokens", "output_tokens", "cached_tokens"}
    rendered = str(diagnostics)
    assert "tenant-1" not in rendered
    assert "patient-1" not in rendered
    assert "thread-1" not in rendered
