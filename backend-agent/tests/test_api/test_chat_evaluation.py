from __future__ import annotations

import asyncio
from pathlib import Path
from typing import Any

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.agent.events import AgentStreamEvent
from app.api import chat as chat_module
from app.api.chat import router as chat_router
from app.api.sessions import router as sessions_router
from app.memory.store import SqliteMemoryStore
from app.auth import AgentScope

_SCOPE = AgentScope(tenant_id="tenant-1", user_id="user-1", patient_id="patient-1")


class _StubScopeClient:
    def verify(self, **_kwargs):
        return _SCOPE

    def authorize_attachments(self, **kwargs):
        return frozenset(kwargs["object_keys"])


class _StubRuntime:
    async def stream(self, *, thread_id, user_message, metadata, scope):
        del user_message, metadata
        self.last_thread_id = thread_id
        yield AgentStreamEvent(type="token", content="answer text")

    async def get_state(self, thread_id, owner_key):
        del thread_id
        return None


@pytest.fixture
def fake_evaluate(monkeypatch: pytest.MonkeyPatch):
    captured: dict[str, Any] = {"result": None}

    def _set_result(result: dict[str, Any]) -> None:
        captured["result"] = result

    async def _fake_evaluate(**kwargs: Any) -> dict[str, Any]:
        del kwargs
        if captured["result"] is None:
            raise RuntimeError("fake_evaluate: set result via _set_result")
        return captured["result"]

    monkeypatch.setattr(chat_module, "evaluate_answer", _fake_evaluate)
    return captured, _set_result


def _create_client(tmp_path: Path) -> TestClient:
    app = FastAPI()
    memory_store = SqliteMemoryStore(str(tmp_path / "memory.db"))
    asyncio.run(memory_store.initialize())
    app.state.memory_store = memory_store
    app.state.agent_runtime = _StubRuntime()
    app.state.agent_scope_client = _StubScopeClient()
    app.include_router(chat_router)
    app.include_router(sessions_router)
    client = TestClient(app)
    client.headers.update({"Authorization": "Bearer test-token", "X-Patient-Id": "patient-1"})
    return client


def test_chat_stream_emits_evaluation_before_done(tmp_path: Path, fake_evaluate):
    _, set_result = fake_evaluate
    set_result({"status": "available", "overall_score": 91, "risk_level": "low", "summary": "reasonable", "issues": [], "suggestions": []})
    client = _create_client(tmp_path)
    try:
        with client.stream("POST", "/api/v1/chat", json={"message": "hello", "metadata": {"entry": "agent_page"}}) as response:
            chunks = "".join(response.iter_text())
        assert chunks.index("event: evaluation") < chunks.index("event: done")
        assert "overall_score" in chunks
    finally:
        client.close()
        asyncio.run(client.app.state.memory_store.close())


def test_chat_stream_persists_unavailable_evaluation(tmp_path: Path, fake_evaluate):
    _, set_result = fake_evaluate
    set_result({"status": "unavailable", "error": "复核暂不可用"})
    client = _create_client(tmp_path)
    try:
        with client.stream("POST", "/api/v1/chat", json={"message": "hello", "metadata": {"entry": "agent_page"}}) as response:
            chunks = "".join(response.iter_text())
        thread_id = chunks.split('event: session\ndata: {"thread_id": "')[1].split('"')[0]
        detail = client.get(f"/api/v1/sessions/{thread_id}").json()
        eval_events = [e for e in detail["turns"][0]["trace_events"] if e["event"] == "evaluation"]
        assert eval_events[0]["data"]["status"] == "unavailable"
        assert "event: done" in chunks
    finally:
        client.close()
        asyncio.run(client.app.state.memory_store.close())
