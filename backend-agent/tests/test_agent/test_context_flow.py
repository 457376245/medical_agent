from __future__ import annotations

import uuid
from typing import Any

from app.agent.context import context_signature_from_metadata, should_refresh_context
from app.agent.runtime import AgentRuntime
from app.agent.state import AgentRuntimeState
from app.services import disease_profile_context as context_client_module
from app.services.disease_profile_context import DiseaseProfileContextClient
from app.tools.registry import ToolSpec


def test_context_signature_and_refresh_rules() -> None:
    metadata = {"disease_profile_id": "profile-1", "record_id": "record-1"}
    assert context_signature_from_metadata(metadata) == "profile-1:record-1"
    assert should_refresh_context(metadata=metadata, active_context_signature=None) is True
    assert should_refresh_context(metadata=metadata, active_context_signature="profile-1:record-1") is False
    assert should_refresh_context(
        metadata={"disease_profile_id": ""},
        active_context_signature="profile-1:record-1",
    ) is False


def test_context_preload_forces_tool_call_on_new_signature() -> None:
    def context_tool(**_kwargs: Any) -> str:
        return '{"context_status":"ready","disease_profile":{"id":"profile-1"}}'

    runtime = AgentRuntime(
        all_tools=[
            ToolSpec(
                name="fetch_disease_profile_context",
                description="context",
                parameters={},
                handler=context_tool,
            )
        ],
        model_tools=[],
    )
    state = AgentRuntimeState(thread_id="thread-1")

    import asyncio

    asyncio.run(
        runtime._preload_context_if_needed(  # noqa: SLF001
            state,
            {"disease_profile_id": "profile-1", "record_id": "record-1"},
        )
    )

    assert state.active_context_signature == "profile-1:record-1"
    assert state.active_context_status == "ready"
    assert state.active_context_bundle["disease_profile"]["id"] == "profile-1"
    tool_calls = state.messages[0].tool_calls
    assert len(tool_calls) == 1
    assert tool_calls[0].name == "fetch_disease_profile_context"
    assert tool_calls[0].args["disease_profile_id"] == "profile-1"
    assert tool_calls[0].args["record_id"] == "record-1"
    assert tool_calls[0].id.startswith("context-")
    assert uuid.UUID(hex=tool_calls[0].id.removeprefix("context-")).version == 7


def test_context_preload_skips_when_signature_unchanged() -> None:
    runtime = AgentRuntime(
        all_tools=[],
        model_tools=[],
    )
    state = AgentRuntimeState(
        thread_id="thread-1",
        active_context_signature="profile-1:record-1",
    )

    import asyncio

    asyncio.run(
        runtime._preload_context_if_needed(  # noqa: SLF001
            state,
            {"disease_profile_id": "profile-1", "record_id": "record-1"},
        )
    )

    assert state.messages == []
    assert state.active_context_signature == "profile-1:record-1"


def test_context_client_success_and_partial(monkeypatch: Any) -> None:
    responses: list[dict[str, Any]] = [
        {
            "profile": {"id": "profile-1", "name": "高血压", "recordCount": 2},
            "contextStatus": "READY",
            "warnings": [],
        },
        {
            "profile": {"id": "profile-1", "name": "高血压", "recordCount": 2},
            "contextStatus": "PARTIAL",
            "warnings": ["报告解析尚未完成"],
        },
    ]

    def fake_get_json(*_args: Any, **_kwargs: Any) -> dict[str, Any]:
        return responses.pop(0)

    monkeypatch.setattr(context_client_module, "_http_get_json", fake_get_json)

    client = DiseaseProfileContextClient(
        base_url="http://35.208.147.180:8080",
        context_path="/internal/agent",
        timeout_seconds=3,
    )

    ready_bundle = client.fetch_context_bundle(disease_profile_id="profile-1")
    partial_bundle = client.fetch_context_bundle(disease_profile_id="profile-1")

    assert ready_bundle["context_status"] == "ready"
    assert partial_bundle["context_status"] == "partial"
    assert partial_bundle["warnings"] == ["报告解析尚未完成"]


def test_context_client_failure_returns_unavailable(monkeypatch: Any) -> None:
    def fake_get_json(*_args: Any, **_kwargs: Any) -> tuple[int, dict[str, Any]]:
        raise context_client_module._HttpFailure(
            status_code=404,
            code="PROFILE_NOT_FOUND",
            message="disease profile not found",
        )

    monkeypatch.setattr(context_client_module, "_http_get_json", fake_get_json)

    client = DiseaseProfileContextClient(
        base_url="http://35.208.147.180:8080",
        context_path="/internal/agent",
        timeout_seconds=3,
    )
    bundle = client.fetch_context_bundle(disease_profile_id="missing-profile")

    assert bundle["context_status"] == "unavailable"
    assert bundle["error"]["code"] == "PROFILE_NOT_FOUND"
    assert "上下文加载失败" in bundle["warnings"][0]

