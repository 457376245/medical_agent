from __future__ import annotations

import asyncio
import contextlib
import uuid
from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.agent.events import AgentStreamEvent
from app.api.chat import router as chat_router
from app.api.sessions import router as sessions_router
from app.agent.state import AgentRuntimeState
from app.memory.store import SqliteMemoryStore
from app.auth import AgentScope

_SCOPE = AgentScope(tenant_id="tenant-1", user_id="user-1", patient_id="patient-1")
_OTHER_SCOPE = AgentScope(tenant_id="tenant-1", user_id="user-2", patient_id="patient-2")
_AUTH = {"Authorization": "Bearer test-token", "X-Patient-Id": "patient-1"}


class _StubScopeClient:
    def verify(self, **kwargs):
        return _OTHER_SCOPE if kwargs["authorization"].endswith("other-token") else _SCOPE

    def authorize_attachments(self, **kwargs):
        return frozenset(key for key in kwargs["object_keys"] if key != "forbidden.pdf")


class _StubRuntime:
    def __init__(self) -> None:
        self.cleared_thread_id = None

    async def stream(self, *, thread_id, user_message, metadata, scope):  # noqa: ANN001
        del user_message
        assert scope == _SCOPE
        self.last_thread_id = thread_id
        self.last_state = AgentRuntimeState(
            thread_id=thread_id,
            owner_key=_SCOPE.owner_key,
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

    async def get_state(self, thread_id, owner_key):  # noqa: ANN001
        assert owner_key == _SCOPE.owner_key
        if getattr(self, "last_thread_id", None) == thread_id:
            return self.last_state
        return None

    async def get_session_items(self, thread_id):  # noqa: ANN001
        del thread_id
        return []

    async def clear_session(self, thread_id, owner_key):  # noqa: ANN001
        assert owner_key == _SCOPE.owner_key
        self.cleared_thread_id = thread_id


def _create_client(db_path: Path) -> tuple[TestClient, SqliteMemoryStore]:
    app = FastAPI()
    memory_store = SqliteMemoryStore(str(db_path))
    asyncio.run(memory_store.initialize())
    app.state.memory_store = memory_store
    app.state.agent_runtime = _StubRuntime()
    app.state.agent_scope_client = _StubScopeClient()
    app.include_router(chat_router)
    app.include_router(sessions_router)
    client = TestClient(app)
    client.headers.update(_AUTH)
    return client, memory_store


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


def test_unowned_sdk_session_items_are_not_visible(tmp_path: Path) -> None:
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
    app.state.agent_scope_client = _StubScopeClient()
    app.include_router(sessions_router)
    client = TestClient(app)
    try:
        response = client.get("/api/v1/sessions/thread-1", headers=_AUTH)

        payload = response.json()
        assert payload == {"thread_id": "thread-1", "messages": [], "found": False}
    finally:
        client.close()
        asyncio.run(memory_store.close())


def test_session_scope_rejects_missing_token_cross_owner_and_unowned_rows(tmp_path: Path) -> None:
    client, memory_store = _create_client(tmp_path / "scope-memory.db")
    try:
        assert client.get("/api/v1/sessions", headers={"Authorization": ""}).status_code == 401
        thread_id = client.post("/api/v1/sessions").json()["thread_id"]
        other_headers = {"Authorization": "Bearer other-token", "X-Patient-Id": "patient-2"}
        assert client.get(f"/api/v1/sessions/{thread_id}", headers=other_headers).json()["found"] is False
        assert client.patch(f"/api/v1/sessions/{thread_id}", headers=other_headers, json={"title": "x"}).status_code == 404
        assert client.delete(f"/api/v1/sessions/{thread_id}", headers=other_headers).status_code == 404
        assert client.post(
            "/api/v1/chat",
            headers=other_headers,
            json={"thread_id": thread_id, "message": "cross owner"},
        ).status_code == 404

        async def insert_legacy_row() -> None:
            await memory_store._conn.execute(  # noqa: SLF001
                "INSERT INTO agent_sessions (thread_id, created_at, updated_at) VALUES (?, ?, ?)",
                ("legacy-thread", "2026-01-01T00:00:00+00:00", "2026-01-01T00:00:00+00:00"),
            )
            await memory_store._conn.commit()  # noqa: SLF001

        asyncio.run(insert_legacy_row())
        assert client.get("/api/v1/sessions").json()["count"] == 1
        assert client.get("/api/v1/sessions/legacy-thread").json()["found"] is False
    finally:
        client.close()
        asyncio.run(memory_store.close())


def test_chat_rejects_attachment_not_authorized_by_java_scope(tmp_path: Path) -> None:
    client, memory_store = _create_client(tmp_path / "attachment-memory.db")
    try:
        response = client.post(
            "/api/v1/chat",
            json={
                "message": "parse",
                "attachments": [{"object_key": "forbidden.pdf", "file_type": "PDF"}],
            },
        )
        assert response.status_code == 403
    finally:
        client.close()
        asyncio.run(memory_store.close())

def test_public_turn_metadata_uses_allowlist() -> None:
    from app.api.chat import _public_turn_metadata

    metadata = {
        "disease_profile_id": "profile-1",
        "disease_name": "糖尿病",
        "entry": "agent_page",
        "context_signature": "profile-1:record-1",
        "patient_id": "patient-secret",
        "id_card": "secret-id",
        "raw_record_text": "secret text",
        "unknown_field": "should-not-appear",
        "attachments": [
            {
                "file_type": "pdf",
                "display_name": "化验单.pdf",
                "object_key": "records/a.pdf",
            }
        ],
    }

    public = _public_turn_metadata(metadata)

    assert public["disease_profile_id"] == "profile-1"
    assert public["entry"] == "agent_page"
    assert "patient_id" not in public
    assert "id_card" not in public
    assert "raw_record_text" not in public
    assert "unknown_field" not in public
    assert public["attachments"] == [
        {"file_type": "pdf", "display_name": "化验单.pdf"}
    ]


def test_public_turn_metadata_skips_non_list_attachments() -> None:
    from app.api.chat import _public_turn_metadata

    public = _public_turn_metadata(
        {
            "entry": "agent_page",
            "attachments": {"object_key": "secret"},
        }
    )

    assert public["entry"] == "agent_page"
    assert "attachments" not in public


def test_thread_stream_lock_acquire_cancel_does_not_leak_ref_count() -> None:
    from app.api.chat import _ThreadStreamLockRegistry

    async def run() -> None:
        registry = _ThreadStreamLockRegistry()
        holder = await registry.acquire("thread-cancel")
        assert registry._entries["thread-cancel"].ref_count == 1

        waiter = asyncio.create_task(registry.acquire("thread-cancel"))
        await asyncio.sleep(0.05)
        assert registry._entries["thread-cancel"].ref_count == 2

        waiter.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await waiter

        assert registry._entries["thread-cancel"].ref_count == 1

        await registry.release("thread-cancel", holder)
        assert registry._entries == {}

    asyncio.run(run())


def test_cleanup_stream_tasks_awaits_pending_and_closes_iterator() -> None:
    from app.api.chat import _cleanup_stream_tasks

    class _ClosingIterator:
        def __init__(self) -> None:
            self.closed = False

        async def aclose(self) -> None:
            self.closed = True

    async def run() -> None:
        async def slow_next() -> None:
            await asyncio.sleep(10)

        pending = asyncio.create_task(slow_next())
        await asyncio.sleep(0.05)
        iterator = _ClosingIterator()

        await _cleanup_stream_tasks(pending, iterator)

        assert pending.cancelled() or pending.done()
        assert iterator.closed is True

    asyncio.run(run())


def test_thread_stream_lock_serializes_same_thread() -> None:
    from app.api.chat import _get_thread_stream_lock_registry

    class _AppState:
        pass

    class _App:
        def __init__(self) -> None:
            self.state = _AppState()

    class _Request:
        def __init__(self) -> None:
            self.app = _App()

    async def run() -> None:
        request = _Request()
        registry = _get_thread_stream_lock_registry(request)
        order: list[str] = []

        async def worker(label: str) -> None:
            entry = await registry.acquire("thread-1")
            try:
                order.append(f"{label}-start")
                await asyncio.sleep(0.05)
                order.append(f"{label}-end")
            finally:
                await registry.release("thread-1", entry)

        await asyncio.gather(worker("a"), worker("b"))

        assert order == ["a-start", "a-end", "b-start", "b-end"]

    asyncio.run(run())

