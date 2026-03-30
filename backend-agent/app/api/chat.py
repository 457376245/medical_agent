"""SSE streaming chat endpoint.

Receives user messages, invokes the Agent graph, and streams token-level
responses back as Server-Sent Events (text/event-stream).
"""

from __future__ import annotations

import json
import logging
import uuid
from collections.abc import AsyncGenerator
from typing import Any

from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessageChunk, HumanMessage

from app.schemas.chat import ChatRequest

LOGGER = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/chat", tags=["chat"])


def _sse_event(event: str, data: dict[str, Any]) -> str:
    """Format a single SSE event line."""
    payload = json.dumps(data, ensure_ascii=False)
    return f"event: {event}\ndata: {payload}\n\n"


@router.post("")
async def chat(body: ChatRequest, request: Request) -> StreamingResponse:
    """Stream an Agent response as Server-Sent Events.

    If ``thread_id`` is provided the session is resumed; otherwise a new
    session is created.
    """
    graph = request.app.state.agent_graph
    thread_id = body.thread_id or uuid.uuid4().hex

    config = {"configurable": {"thread_id": thread_id}}

    input_msg = {"messages": [HumanMessage(content=body.message)]}

    async def event_stream() -> AsyncGenerator[str, None]:
        # Signal session info
        yield _sse_event("session", {"thread_id": thread_id})

        full_content: list[str] = []

        try:
            async for event in graph.astream_events(
                input_msg, config=config, version="v2"
            ):
                kind = event.get("event", "")

                # LLM token stream
                if kind == "on_chat_model_stream":
                    chunk = event.get("data", {}).get("chunk")
                    if isinstance(chunk, AIMessageChunk) and chunk.content:
                        token = str(chunk.content)
                        full_content.append(token)
                        yield _sse_event("token", {"content": token})

                # Tool invocation start
                elif kind == "on_tool_start":
                    tool_name = event.get("name", "unknown")
                    tool_input = event.get("data", {}).get("input", {})
                    yield _sse_event(
                        "tool_call",
                        {"tool": tool_name, "input": tool_input},
                    )

                # Tool invocation end
                elif kind == "on_tool_end":
                    tool_name = event.get("name", "unknown")
                    tool_output = event.get("data", {}).get("output", "")
                    yield _sse_event(
                        "tool_result",
                        {
                            "tool": tool_name,
                            "output": str(tool_output)[:2000],
                        },
                    )

            # Final done event
            yield _sse_event(
                "done",
                {
                    "thread_id": thread_id,
                    "content": "".join(full_content),
                },
            )

        except Exception as exc:
            LOGGER.exception("SSE stream error for thread %s", thread_id)
            yield _sse_event("error", {"message": str(exc)})

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )
