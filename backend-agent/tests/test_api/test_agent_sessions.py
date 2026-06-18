from __future__ import annotations

import asyncio
import uuid
from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.agent.events import AgentStreamEvent
from app.api.chat import router as chat_router
from app.api.sessions import router as sessions_router
from app.agent.state import AgentRuntimeState
from app.memory.store import SqliteMemoryStore


class _StubRuntime:
    def __init__(self) -> None:
        self.cleared_thread_id = None

    async def stream(self, *, thread_id, user_message, metadata):  # noqa: ANN001
        del user_message
        self.last_thread_id = thread_id
        self.last_state = AgentRuntimeState(
            thread_id=thread_id,
            active_context_signature="profile-1:record-1"
            if metadata.get("disease_profile_id") == "profile-1"
            else metadata.get("disease_profile_id"),
            active_context_status="ready",
        )
        yield AgentStreamEvent(
            type="tool_call",
            tool="parse_document",
            data={"input": {"object_key": "records/a.pdf"}},
        )
        yield AgentStreamEvent(
            type="tool_result",
            tool="parse_document",
            data={"output": "提取到 3 项关键指标"},
        )
        yield AgentStreamEvent(type="token", content="第一段回答。")
        yield AgentStreamEvent(type="token", content="第二段回答。")

    async def get_state(self, thread_id):  # noqa: ANN001
        if getattr(self, "last_thread_id", None) == thread_id:
            return self.last_state
        return None

    async def get_session_items(self, thread_id):  # noqa: ANN001
        del thread_id
        return []

    async def clear_session(self, thread_id):  # noqa: ANN001
        self.cleared_thread_id = thread_id


def _create_client(db_path: Path) -> tuple[TestClient, SqliteMemoryStore]:
    app = FastAPI()
    memory_store = SqliteMemoryStore(str(db_path))
    asyncio.run(memory_store.initialize())
    app.state.memory_store = memory_store
    app.state.agent_runtime = _StubRuntime()
    app.include_router(chat_router)
    app.include_router(sessions_router)
    return TestClient(app), memory_store


def test_chat_stream_persists_session_index_and_turn_trace(tmp_path: Path) -> None:
    client, memory_store = _create_client(tmp_path / "memory.db")
    try:
        with client.stream(
            "POST",
            "/api/v1/chat",
            json={
                "message": "请解释本次化验单的异常点",
                "metadata": {
                    "disease_profile_id": "profile-1",
                    "disease_name": "糖尿病",
                    "record_id": "record-1",
                    "record_title": "门诊化验单",
                    "entry": "agent_page",
                },
            },
        ) as response:
            chunks = "".join(response.iter_text())

        assert "event: session" in chunks
        assert "event: tool_call" in chunks
        assert "event: tool_result" in chunks
        assert "event: token" in chunks
        assert "event: done" in chunks
        assert "records/a.pdf" not in chunks

        sessions_response = client.get("/api/v1/sessions?disease_profile_id=profile-1")
        sessions_payload = sessions_response.json()
        assert sessions_payload["count"] == 1
        session = sessions_payload["sessions"][0]
        assert session["disease_name"] == "糖尿病"
        assert session["record_title"] == "门诊化验单"
        assert session["context_signature"] == "profile-1:record-1"
        assert session["turn_count"] == 1
        assert uuid.UUID(hex=session["thread_id"]).version == 7

        detail_response = client.get(f"/api/v1/sessions/{session['thread_id']}")
        detail_payload = detail_response.json()
        assert detail_payload["found"] is True
        assert detail_payload["message_count"] == 2
        assert len(detail_payload["turns"]) == 1
        assert uuid.UUID(hex=detail_payload["turns"][0]["turn_id"]).version == 7
        assert detail_payload["turns"][0]["metadata"]["disease_profile_id"] == "profile-1"
        assert detail_payload["turns"][0]["metadata"]["context_signature"] == "profile-1:record-1"
        assert detail_payload["turns"][0]["trace_events"][0]["event"] == "tool_call"
        assert detail_payload["turns"][0]["trace_events"][1]["event"] == "tool_result"
        assert "records/a.pdf" not in str(detail_payload["turns"][0]["trace_events"])
        assert detail_payload["messages"][0]["role"] == "user"
        assert detail_payload["messages"][1]["content"] == "第一段回答。第二段回答。"
    finally:
        client.close()
        asyncio.run(memory_store.close())


def test_delete_session_removes_indexed_data(tmp_path: Path) -> None:
    client, memory_store = _create_client(tmp_path / "delete-memory.db")
    try:
        with client.stream(
            "POST",
            "/api/v1/chat",
            json={
                "message": "生成复诊建议",
                "metadata": {
                    "disease_profile_id": "profile-2",
                    "disease_name": "高血压",
                    "entry": "agent_page",
                },
            },
        ) as response:
            chunks = "".join(response.iter_text())
        thread_id = chunks.split('event: session\ndata: {"thread_id": "')[1].split('"')[0]

        delete_response = client.delete(f"/api/v1/sessions/{thread_id}")
        assert delete_response.json()["deleted"] is True

        list_response = client.get("/api/v1/sessions?disease_profile_id=profile-2")
        assert list_response.json()["count"] == 0
        assert client.app.state.agent_runtime.cleared_thread_id == thread_id
    finally:
        client.close()
        asyncio.run(memory_store.close())


def test_create_session_returns_uuid7_thread_id(tmp_path: Path) -> None:
    client, memory_store = _create_client(tmp_path / "create-memory.db")
    try:
        response = client.post("/api/v1/sessions")

        assert response.status_code == 200
        assert uuid.UUID(hex=response.json()["thread_id"]).version == 7
    finally:
        client.close()
        asyncio.run(memory_store.close())


def test_session_detail_falls_back_to_sdk_session_items(tmp_path: Path) -> None:
    app = FastAPI()
    memory_store = SqliteMemoryStore(str(tmp_path / "fallback-memory.db"))
    asyncio.run(memory_store.initialize())

    class RuntimeWithItems:
        async def get_session_items(self, thread_id):  # noqa: ANN001
            assert thread_id == "thread-1"
            return [
                {"role": "user", "content": "你好"},
                {
                    "role": "assistant",
                    "content": [{"type": "output_text", "text": "请问哪里不舒服？"}],
                },
                {"role": "tool", "content": "hidden"},
            ]

    app.state.memory_store = memory_store
    app.state.agent_runtime = RuntimeWithItems()
    app.include_router(sessions_router)
    client = TestClient(app)
    try:
        response = client.get("/api/v1/sessions/thread-1")

        payload = response.json()
        assert payload["found"] is True
        assert payload["message_count"] == 2
        assert payload["messages"] == [
            {"role": "user", "content": "你好"},
            {"role": "assistant", "content": "请问哪里不舒服？"},
        ]
    finally:
        client.close()
        asyncio.run(memory_store.close())
