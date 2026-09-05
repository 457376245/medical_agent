"""SSE 流式聊天端点。

接收用户消息，调用 Agent 图，并将 token 级别的响应以 Server-Sent Events
（text/event-stream）流式返回。
"""

from __future__ import annotations

import asyncio
import contextlib
import json
import logging
from collections.abc import AsyncGenerator, AsyncIterator
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse

from app.agent.context import context_signature_from_metadata
from app.agent.evaluator import build_grounded_evaluation_context, evaluate_answer
from app.agent.events import AgentStreamEvent
from app.api.tool_events import sanitize_tool_input, sanitize_tool_output
from app.auth import AgentScope, require_agent_scope
from app.ids import new_ordered_id
from app.memory.models import AgentSessionRecord, AgentSessionTurn, AgentTraceEvent
from app.schemas.chat import ChatRequest

LOGGER = logging.getLogger(__name__)

_SSE_KEEPALIVE_INTERVAL = 15.0

_STREAM_END = object()

_THREAD_STREAM_LOCKS_ATTR = "_chat_thread_stream_locks"

_PUBLIC_TURN_METADATA_KEYS = frozenset(
    {
        "disease_profile_id",
        "disease_name",
        "record_id",
        "record_title",
        "record_date",
        "source_type",
        "workflow",
        "scenario",
        "audience",
        "urgency_level",
        "entry",
        "context_signature",
        "context_status",
        "attachments",
    }
)


@dataclass
class _ThreadStreamLockEntry:
    lock: asyncio.Lock
    ref_count: int = 0


class _ThreadStreamLockRegistry:
    def __init__(self) -> None:
        self._entries: dict[str, _ThreadStreamLockEntry] = {}
        self._guard = asyncio.Lock()

    async def acquire(self, thread_id: str) -> _ThreadStreamLockEntry:
        async with self._guard:
            entry = self._entries.get(thread_id)
            if entry is None:
                entry = _ThreadStreamLockEntry(lock=asyncio.Lock())
                self._entries[thread_id] = entry
            entry.ref_count += 1
        try:
            await entry.lock.acquire()
        except BaseException:
            async with self._guard:
                entry.ref_count -= 1
                if entry.ref_count <= 0:
                    self._entries.pop(thread_id, None)
            raise
        return entry

    async def release(self, thread_id: str, entry: _ThreadStreamLockEntry) -> None:
        entry.lock.release()
        async with self._guard:
            entry.ref_count -= 1
            if entry.ref_count <= 0:
                self._entries.pop(thread_id, None)


def _get_thread_stream_lock_registry(request: Request) -> _ThreadStreamLockRegistry:
    registry = getattr(request.app.state, _THREAD_STREAM_LOCKS_ATTR, None)
    if registry is None:
        registry = _ThreadStreamLockRegistry()
        setattr(request.app.state, _THREAD_STREAM_LOCKS_ATTR, registry)
    return registry


@contextlib.asynccontextmanager
async def _thread_stream_lock(request: Request, thread_id: str) -> AsyncGenerator[None, None]:
    registry = _get_thread_stream_lock_registry(request)
    entry = await registry.acquire(thread_id)
    try:
        yield
    finally:
        await registry.release(thread_id, entry)


async def _next_event(aiter: Any) -> Any:
    """安全获取异步迭代器的下一个元素，避免 StopAsyncIteration 在异步生成器中触发 RuntimeError。"""
    try:
        return await aiter.__anext__()
    except StopAsyncIteration:
        return _STREAM_END

router = APIRouter(
    prefix="/api/v1/chat",
    tags=["chat"],
    dependencies=[Depends(require_agent_scope)],
)


def _sse_event(event: str, data: dict[str, Any]) -> str:
    """格式化单个 SSE 事件行。"""
    payload = json.dumps(data, ensure_ascii=False)
    return f"event: {event}\ndata: {payload}\n\n"


def _trim_text(value: str | None, limit: int = 72) -> str:
    text = (value or "").strip()
    if len(text) <= limit:
        return text
    return f"{text[: limit - 1].rstrip()}…"


def _derive_title(existing_title: str | None, user_message: str) -> str:
    if existing_title:
        return existing_title
    return _trim_text(user_message, limit=28) or "新对话"


def _friendly_stream_error(exc: Exception) -> str:
    message = str(exc).strip() or "agent stream failed"
    lowered = message.lower()
    if "unexpected_eof_while_reading" in lowered or "ssl" in lowered and "eof" in lowered:
        return (
            "LLM 网络握手失败（SSL EOF）。请检查代理设置，"
            "建议将 `LLM_PROXY_MODE` 设为 `bypass_google` 或 `off` 后重试。"
        )
    if "connecterror" in lowered or "connection" in lowered and "failed" in lowered:
        return "LLM 网络连接失败，请检查外网连通性、代理配置和 API Key。"
    return message


def _public_turn_metadata(metadata: dict[str, Any]) -> dict[str, Any]:
    """仅保留明确允许通过会话详情展示的字段。"""
    public: dict[str, Any] = {}
    for key in _PUBLIC_TURN_METADATA_KEYS:
        if key not in metadata:
            continue
        value = metadata[key]
        if key == "attachments":
            if isinstance(value, list):
                public["attachments"] = [
                    {
                        "file_type": item.get("file_type"),
                        "display_name": item.get("display_name"),
                    }
                    for item in value
                    if isinstance(item, dict)
                ]
            continue
        public[key] = value
    return public


def _session_from_metadata(
    *,
    thread_id: str,
    owner_key: str,
    existing: AgentSessionRecord | None,
    user_message: str,
    assistant_message: str,
    metadata: dict[str, Any],
) -> AgentSessionRecord:
    def pick(key: str, fallback: str | None = None) -> str | None:
        value = metadata.get(key)
        if value is None or value == "":
            return fallback
        rendered = str(value).strip()
        return rendered or fallback

    preview_source = assistant_message.strip() or user_message
    derived_signature = context_signature_from_metadata(metadata)
    explicit_context_signature = pick(
        "context_signature",
        existing.context_signature if existing else None,
    )
    context_signature = explicit_context_signature or derived_signature
    context_status = pick(
        "context_status",
        existing.context_status if existing else None,
    )

    return AgentSessionRecord(
        thread_id=thread_id,
        owner_key=owner_key,
        disease_profile_id=pick("disease_profile_id", existing.disease_profile_id if existing else None),
        disease_name=pick("disease_name", existing.disease_name if existing else None),
        record_id=pick("record_id", existing.record_id if existing else None),
        record_title=pick("record_title", existing.record_title if existing else None),
        record_date=pick("record_date", existing.record_date if existing else None),
        source_type=pick("source_type", existing.source_type if existing else None),
        context_signature=context_signature,
        context_status=context_status,
        title=_derive_title(existing.title if existing else None, user_message),
        last_user_message=user_message,
        last_assistant_message=assistant_message or (existing.last_assistant_message if existing else None),
        last_message_preview=_trim_text(preview_source, limit=120),
        created_at=existing.created_at if existing is not None else datetime.now(timezone.utc),
        updated_at=datetime.now(timezone.utc),
        turn_count=(existing.turn_count if existing is not None else 0),
    )


@dataclass
class ChatTurnContext:
    thread_id: str
    scope: AgentScope
    turn_metadata: dict[str, Any]
    turn_index: int


@dataclass
class ChatStreamState:
    full_content: list[str] = field(default_factory=list)
    trace_events: list[AgentTraceEvent] = field(default_factory=list)
    error_message: str | None = None
    evaluation_payload: dict[str, Any] | None = None
    pending_next: asyncio.Task[Any] | None = None
    stream_aiter: AsyncIterator[Any] | None = None


async def _prepare_chat_context(
    *,
    body: ChatRequest,
    request: Request,
    memory_store: Any,
    thread_id: str,
    scope: AgentScope,
) -> ChatTurnContext:
    turn_metadata: dict[str, Any] = body.metadata.to_runtime_metadata()
    for identity_key in ("tenant_id", "user_id", "patient_id", "owner_key"):
        turn_metadata.pop(identity_key, None)
    if body.attachments:
        requested_keys = [attachment.object_key for attachment in body.attachments]
        authorized_keys = await asyncio.to_thread(
            request.app.state.agent_scope_client.authorize_attachments,
            scope=scope,
            object_keys=requested_keys,
        )
        if any(key not in authorized_keys for key in requested_keys):
            raise HTTPException(status_code=403, detail="attachment is not authorized for this patient")
        turn_metadata["attachments"] = [
            attachment.model_dump(exclude_none=True) for attachment in body.attachments
        ]

    existing_session: AgentSessionRecord | None = None
    turn_index = 1
    if memory_store is not None:
        existing_session = await memory_store.get_agent_session(thread_id, scope.owner_key)
        if body.thread_id and existing_session is None:
            raise HTTPException(status_code=404, detail="session not found")
        turn_index = (existing_session.turn_count if existing_session is not None else 0) + 1
        await memory_store.upsert_agent_session(
            _session_from_metadata(
                thread_id=thread_id,
                owner_key=scope.owner_key,
                existing=existing_session,
                user_message=body.message,
                assistant_message="",
                metadata=turn_metadata,
            )
        )

    return ChatTurnContext(
        thread_id=thread_id,
        scope=scope,
        turn_metadata=turn_metadata,
        turn_index=turn_index,
    )


async def _enrich_metadata_from_runtime_state(
    *,
    runtime: Any,
    thread_id: str,
    owner_key: str,
    turn_metadata: dict[str, Any],
    trace_events: list[AgentTraceEvent],
    evaluation_payload: dict[str, Any] | None,
) -> None:
    try:
        latest_state = await runtime.get_state(thread_id, owner_key)
        if latest_state is None:
            return
        state_signature = latest_state.active_context_signature
        state_status = latest_state.active_context_status
        if state_signature is not None and str(state_signature).strip():
            turn_metadata["context_signature"] = str(state_signature).strip()
        elif context_signature_from_metadata(turn_metadata):
            turn_metadata["context_signature"] = context_signature_from_metadata(turn_metadata)
        if state_status is not None and str(state_status).strip():
            turn_metadata["context_status"] = str(state_status).strip().lower()
        if latest_state.last_diagnostics:
            diagnostics = dict(latest_state.last_diagnostics)
            diagnostics["evaluator"] = {
                "status": (evaluation_payload or {}).get("status", "unavailable"),
                "rubric_version": (evaluation_payload or {}).get("rubric_version"),
                "latency_ms": (evaluation_payload or {}).get("latency_ms", 0.0),
            }
            trace_events.append(AgentTraceEvent(event="diagnostics", data=diagnostics))
    except Exception as exc:
        LOGGER.debug("Failed to read final agent state for %s: %s", thread_id, exc)


def _handle_stream_event(
    event: AgentStreamEvent,
    *,
    full_content: list[str],
    trace_events: list[AgentTraceEvent],
) -> list[str]:
    sse_chunks: list[str] = []
    if event.type == "token":
        token = str(event.content)
        if token:
            full_content.append(token)
            sse_chunks.append(_sse_event("token", {"content": token}))
    elif event.type == "tool_call":
        tool_name = event.tool or "unknown"
        tool_input = event.data.get("input", {})
        public_input = sanitize_tool_input(str(tool_name), tool_input)
        trace_events.append(
            AgentTraceEvent(
                event="tool_call",
                tool=str(tool_name),
                data={"input": public_input},
            )
        )
        sse_chunks.append(
            _sse_event(
                "tool_call",
                {"tool": tool_name, "input": public_input, **public_input},
            )
        )
    elif event.type == "tool_result":
        tool_name = event.tool or "unknown"
        tool_output = event.data.get("output", "")
        public_output = sanitize_tool_output(str(tool_name), tool_output)
        trace_events.append(
            AgentTraceEvent(
                event="tool_result",
                tool=str(tool_name),
                data={"output": public_output},
            )
        )
        sse_chunks.append(
            _sse_event(
                "tool_result",
                {
                    "tool": tool_name,
                    "output": public_output,
                    **public_output,
                },
            )
        )
    return sse_chunks


async def _submit_patient_memory_extraction(
    *,
    request: Request,
    thread_id: str,
    saved_turn: AgentSessionTurn,
    turn_metadata: dict[str, Any],
    scope: AgentScope,
) -> None:
    patient_memory_extractor = getattr(request.app.state, "patient_memory_extractor", None)
    if patient_memory_extractor is None or not saved_turn.assistant_message.strip():
        return
    try:
        submitted_count = await asyncio.to_thread(
            patient_memory_extractor.extract_and_submit,
            thread_id=thread_id,
            turn_id=saved_turn.turn_id,
            user_message=saved_turn.user_message,
            assistant_message=saved_turn.assistant_message,
            metadata=turn_metadata,
            scope=scope,
        )
        if submitted_count:
            LOGGER.info(
                "Submitted %d patient memory candidates for thread %s",
                submitted_count,
                thread_id,
            )
    except Exception as exc:
        LOGGER.warning("Failed to extract patient memories for %s: %s", thread_id, exc)


async def _persist_stream_result(
    *,
    runtime: Any,
    memory_store: Any,
    request: Request,
    thread_id: str,
    turn_index: int,
    user_message: str,
    stream_state: ChatStreamState,
    turn_metadata: dict[str, Any],
    scope: AgentScope,
) -> None:
    assistant_message = "".join(stream_state.full_content)
    should_persist = memory_store is not None and (
        user_message.strip() or assistant_message or stream_state.trace_events
    )
    if not should_persist:
        return
    try:
        await _enrich_metadata_from_runtime_state(
            runtime=runtime,
            thread_id=thread_id,
            owner_key=scope.owner_key,
            turn_metadata=turn_metadata,
            trace_events=stream_state.trace_events,
            evaluation_payload=stream_state.evaluation_payload,
        )
        saved_existing = await memory_store.get_agent_session(thread_id, scope.owner_key)
        saved_turn = await memory_store.save_agent_turn(
            AgentSessionTurn(
                thread_id=thread_id,
                owner_key=scope.owner_key,
                turn_index=turn_index,
                user_message=user_message,
                assistant_message=assistant_message,
                trace_events=stream_state.trace_events,
                metadata=_public_turn_metadata(turn_metadata),
                error_message=stream_state.error_message,
            )
        )
        updated_session = _session_from_metadata(
            thread_id=thread_id,
            owner_key=scope.owner_key,
            existing=saved_existing,
            user_message=saved_turn.user_message,
            assistant_message=saved_turn.assistant_message,
            metadata=turn_metadata,
        )
        updated_session.turn_count = turn_index
        await memory_store.upsert_agent_session(updated_session)
        await _submit_patient_memory_extraction(
            request=request,
            thread_id=thread_id,
            saved_turn=saved_turn,
            turn_metadata=turn_metadata,
            scope=scope,
        )
    except Exception as exc:
        LOGGER.warning("Failed to persist turn for %s: %s", thread_id, exc)


async def _cleanup_stream_tasks(
    pending_next: asyncio.Task[Any] | None,
    stream_aiter: AsyncIterator[Any] | None,
) -> None:
    if pending_next is not None and not pending_next.done():
        pending_next.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await pending_next
    if stream_aiter is not None:
        aclose = getattr(stream_aiter, "aclose", None)
        if callable(aclose):
            with contextlib.suppress(Exception):
                await aclose()


@router.post("")
async def chat(body: ChatRequest, request: Request) -> StreamingResponse:
    """将 Agent 响应以 Server-Sent Events 流式返回。

    如果提供了 thread_id，则恢复会话；否则创建新会话。
    """
    runtime = request.app.state.agent_runtime
    memory_store = getattr(request.app.state, "memory_store", None)
    scope: AgentScope = request.state.agent_scope
    thread_id = body.thread_id or new_ordered_id()
    turn_context = await _prepare_chat_context(
        body=body,
        request=request,
        memory_store=memory_store,
        thread_id=thread_id,
        scope=scope,
    )
    turn_metadata = turn_context.turn_metadata
    turn_index = turn_context.turn_index

    async def event_stream() -> AsyncGenerator[str, None]:
        async with _thread_stream_lock(request, thread_id):
            yield _sse_event("session", {"thread_id": thread_id})

            stream_state = ChatStreamState()

            try:
                stream_state.stream_aiter = runtime.stream(
                    thread_id=thread_id,
                    user_message=body.message,
                    metadata=turn_metadata,
                    scope=scope,
                ).__aiter__()

                while True:
                    if stream_state.pending_next is None:
                        stream_state.pending_next = asyncio.ensure_future(
                            _next_event(stream_state.stream_aiter)
                        )

                    done, _ = await asyncio.wait(
                        {stream_state.pending_next}, timeout=_SSE_KEEPALIVE_INTERVAL
                    )
                    if not done:
                        yield ": keepalive\n\n"
                        continue

                    event = stream_state.pending_next.result()
                    stream_state.pending_next = None
                    if event is _STREAM_END:
                        break

                    if not isinstance(event, AgentStreamEvent):
                        continue

                    for chunk in _handle_stream_event(
                        event,
                        full_content=stream_state.full_content,
                        trace_events=stream_state.trace_events,
                    ):
                        yield chunk

                assistant_message = "".join(stream_state.full_content)
                evaluation_state = await runtime.get_state(thread_id, scope.owner_key)
                stream_state.evaluation_payload = await evaluate_answer(
                    user_message=body.message,
                    assistant_answer=assistant_message,
                    metadata=turn_metadata,
                    grounded_context=build_grounded_evaluation_context(
                        evaluation_state.active_context_bundle if evaluation_state else None,
                        evaluation_state.active_context_status if evaluation_state else None,
                    ),
                )
                stream_state.trace_events.append(
                    AgentTraceEvent(
                        event="evaluation",
                        data=stream_state.evaluation_payload,
                    )
                )
                yield _sse_event("evaluation", stream_state.evaluation_payload)

                yield _sse_event(
                    "done",
                    {
                        "thread_id": thread_id,
                        "content": assistant_message,
                    },
                )

            except asyncio.CancelledError:
                stream_state.error_message = "client disconnected"
                LOGGER.info("SSE stream cancelled for thread %s", thread_id)
                raise
            except Exception as exc:
                stream_state.error_message = _friendly_stream_error(exc)
                stream_state.trace_events.append(
                    AgentTraceEvent(
                        event="error",
                        data={"message": stream_state.error_message},
                    )
                )
                LOGGER.exception("SSE stream error for thread %s", thread_id)
                yield _sse_event("error", {"message": stream_state.error_message})
            finally:
                await _cleanup_stream_tasks(
                    stream_state.pending_next,
                    stream_state.stream_aiter,
                )
                await _persist_stream_result(
                    runtime=runtime,
                    memory_store=memory_store,
                    request=request,
                    thread_id=thread_id,
                    turn_index=turn_index,
                    user_message=body.message,
                    stream_state=stream_state,
                    turn_metadata=turn_metadata,
                    scope=scope,
                )

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )
