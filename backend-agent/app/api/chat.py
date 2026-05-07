"""SSE 流式聊天端点。

接收用户消息，调用 Agent 图，并将 token 级别的响应以 Server-Sent Events
（text/event-stream）流式返回。
"""

from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import AsyncGenerator
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessageChunk, HumanMessage

from app.agent.context import context_signature_from_metadata
from app.api.tool_events import sanitize_tool_input, sanitize_tool_output
from app.ids import new_ordered_id
from app.memory.models import AgentSessionRecord, AgentSessionTurn, AgentTraceEvent
from app.schemas.chat import ChatRequest

LOGGER = logging.getLogger(__name__)

_SSE_KEEPALIVE_INTERVAL = 15.0

_STREAM_END = object()


async def _next_event(aiter: Any) -> Any:
    """安全获取异步迭代器的下一个元素，避免 StopAsyncIteration 在异步生成器中触发 RuntimeError。"""
    try:
        return await aiter.__anext__()
    except StopAsyncIteration:
        return _STREAM_END

router = APIRouter(prefix="/api/v1/chat", tags=["chat"])


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
    """去除不应通过会话详情暴露的运行时字段。"""
    public = dict(metadata)
    public.pop("patient_id", None)
    attachments = public.get("attachments")
    if isinstance(attachments, list):
        public["attachments"] = [
            {
                "file_type": item.get("file_type"),
                "display_name": item.get("display_name"),
            }
            for item in attachments
            if isinstance(item, dict)
        ]
    return public


def _session_from_metadata(
    *,
    thread_id: str,
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


@router.post("")
async def chat(body: ChatRequest, request: Request) -> StreamingResponse:
    """将 Agent 响应以 Server-Sent Events 流式返回。

    如果提供了 thread_id，则恢复会话；否则创建新会话。
    """
    graph = request.app.state.agent_graph
    memory_store = getattr(request.app.state, "memory_store", None)
    thread_id = body.thread_id or new_ordered_id()
    config = {"configurable": {"thread_id": thread_id}}
    turn_metadata: dict[str, Any] = body.metadata.to_graph_metadata()
    if body.attachments:
        turn_metadata["attachments"] = [
            attachment.model_dump(exclude_none=True) for attachment in body.attachments
        ]

    # 将患者范围从 HTTP 头转发到元数据供下游工具使用
    patient_id_header = request.headers.get("X-Patient-Id", "").strip()
    if patient_id_header and not turn_metadata.get("patient_id"):
        turn_metadata["patient_id"] = patient_id_header

    input_msg = {
        "messages": [HumanMessage(content=body.message)],
        "thread_id": thread_id,
        "metadata": turn_metadata,
    }

    existing_session: AgentSessionRecord | None = None
    turn_index = 1
    if memory_store is not None:
        try:
            existing_session = await memory_store.get_agent_session(thread_id)
            turn_index = (existing_session.turn_count if existing_session is not None else 0) + 1
            await memory_store.upsert_agent_session(
                _session_from_metadata(
                    thread_id=thread_id,
                    existing=existing_session,
                    user_message=body.message,
                    assistant_message="",
                    metadata=turn_metadata,
                )
            )
        except Exception as exc:
            LOGGER.warning("Failed to initialise session index for %s: %s", thread_id, exc)

    async def event_stream() -> AsyncGenerator[str, None]:
        yield _sse_event("session", {"thread_id": thread_id})

        full_content: list[str] = []
        trace_events: list[AgentTraceEvent] = []
        error_message: str | None = None
        pending_next: asyncio.Task[Any] | None = None

        try:
            aiter = graph.astream_events(
                input_msg, config=config, version="v2"
            ).__aiter__()

            while True:
                if pending_next is None:
                    pending_next = asyncio.ensure_future(_next_event(aiter))

                done, _ = await asyncio.wait(
                    {pending_next}, timeout=_SSE_KEEPALIVE_INTERVAL
                )
                if not done:
                    yield ": keepalive\n\n"
                    continue

                event = pending_next.result()
                pending_next = None
                if event is _STREAM_END:
                    break

                kind = event.get("event", "")

                if kind == "on_chat_model_stream":
                    chunk = event.get("data", {}).get("chunk")
                    if isinstance(chunk, AIMessageChunk) and chunk.content:
                        token = str(chunk.content)
                        full_content.append(token)
                        yield _sse_event("token", {"content": token})

                elif kind == "on_tool_start":
                    tool_name = event.get("name", "unknown")
                    tool_input = event.get("data", {}).get("input", {})
                    public_input = sanitize_tool_input(str(tool_name), tool_input)
                    trace_events.append(
                        AgentTraceEvent(
                            event="tool_call",
                            tool=str(tool_name),
                            data={"input": public_input},
                        )
                    )
                    yield _sse_event(
                        "tool_call",
                        {"tool": tool_name, "input": public_input, **public_input},
                    )

                elif kind == "on_tool_end":
                    tool_name = event.get("name", "unknown")
                    tool_output = event.get("data", {}).get("output", "")
                    public_output = sanitize_tool_output(str(tool_name), tool_output)
                    trace_events.append(
                        AgentTraceEvent(
                            event="tool_result",
                            tool=str(tool_name),
                            data={"output": public_output},
                        )
                    )
                    yield _sse_event(
                        "tool_result",
                        {
                            "tool": tool_name,
                            "output": public_output,
                            **public_output,
                        },
                    )

            yield _sse_event(
                "done",
                {
                    "thread_id": thread_id,
                    "content": "".join(full_content),
                },
            )

        except asyncio.CancelledError:
            error_message = "client disconnected"
            LOGGER.info("SSE stream cancelled for thread %s", thread_id)
            raise
        except Exception as exc:
            error_message = _friendly_stream_error(exc)
            trace_events.append(
                AgentTraceEvent(
                    event="error",
                    data={"message": error_message},
                )
            )
            LOGGER.exception("SSE stream error for thread %s", thread_id)
            yield _sse_event("error", {"message": error_message})
        finally:
            if pending_next is not None and not pending_next.done():
                pending_next.cancel()
            if memory_store is None:
                return
            assistant_message = "".join(full_content)
            if not body.message.strip() and not assistant_message and not trace_events:
                return
            try:
                try:
                    latest_state = await graph.aget_state(config)
                    values = latest_state.values if latest_state is not None else {}
                    if isinstance(values, dict):
                        state_signature = values.get("active_context_signature")
                        state_status = values.get("active_context_status")
                        if state_signature is not None and str(state_signature).strip():
                            turn_metadata["context_signature"] = str(state_signature).strip()
                        elif context_signature_from_metadata(turn_metadata):
                            turn_metadata["context_signature"] = context_signature_from_metadata(turn_metadata)
                        if state_status is not None and str(state_status).strip():
                            turn_metadata["context_status"] = str(state_status).strip().lower()
                except Exception as exc:
                    LOGGER.debug("Failed to read final graph state for %s: %s", thread_id, exc)

                saved_existing = await memory_store.get_agent_session(thread_id)
                saved_turn = await memory_store.save_agent_turn(
                    AgentSessionTurn(
                        thread_id=thread_id,
                        turn_index=turn_index,
                        user_message=body.message,
                        assistant_message=assistant_message,
                        trace_events=trace_events,
                        metadata=_public_turn_metadata(turn_metadata),
                        error_message=error_message,
                    )
                )
                updated_session = _session_from_metadata(
                    thread_id=thread_id,
                    existing=saved_existing,
                    user_message=saved_turn.user_message,
                    assistant_message=saved_turn.assistant_message,
                    metadata=turn_metadata,
                )
                updated_session.turn_count = turn_index
                await memory_store.upsert_agent_session(updated_session)
            except Exception as exc:
                LOGGER.warning("Failed to persist turn for %s: %s", thread_id, exc)

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )
