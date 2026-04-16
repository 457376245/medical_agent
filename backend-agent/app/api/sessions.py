"""会话管理端点。

对话会话的 CRUD 操作：创建、恢复、列表、删除。
每个会话对应一个 LangGraph thread_id。
"""

from __future__ import annotations

import logging
import uuid
from typing import Any

from fastapi import APIRouter, Query, Request
from pydantic import BaseModel

from app.memory.models import AgentSessionRecord, AgentSessionTurn

LOGGER = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/sessions", tags=["sessions"])


class SessionUpdateRequest(BaseModel):
    title: str


def _serialise_session(session: AgentSessionRecord) -> dict[str, Any]:
    return {
        "thread_id": session.thread_id,
        "disease_profile_id": session.disease_profile_id,
        "disease_name": session.disease_name,
        "record_id": session.record_id,
        "record_title": session.record_title,
        "record_date": session.record_date,
        "source_type": session.source_type,
        "context_signature": session.context_signature,
        "context_status": session.context_status,
        "title": session.title,
        "last_user_message": session.last_user_message,
        "last_assistant_message": session.last_assistant_message,
        "last_message_preview": session.last_message_preview,
        "turn_count": session.turn_count,
        "created_at": session.created_at.isoformat(),
        "updated_at": session.updated_at.isoformat(),
    }


def _serialise_turn(turn: AgentSessionTurn) -> dict[str, Any]:
    return {
        "turn_id": turn.turn_id,
        "turn_index": turn.turn_index,
        "thread_id": turn.thread_id,
        "user_message": turn.user_message,
        "assistant_message": turn.assistant_message,
        "metadata": turn.metadata,
        "error_message": turn.error_message,
        "created_at": turn.created_at.isoformat(),
        "trace_events": [
            {
                "event": event.event,
                "tool": event.tool,
                "data": event.data,
                "created_at": event.created_at.isoformat(),
            }
            for event in turn.trace_events
        ],
    }


def _flatten_turn_messages(turns: list[AgentSessionTurn]) -> list[dict[str, Any]]:
    messages: list[dict[str, Any]] = []
    for turn in turns:
        messages.append(
            {
                "role": "user",
                "content": turn.user_message,
                "turn_id": turn.turn_id,
                "turn_index": turn.turn_index,
                "created_at": turn.created_at.isoformat(),
            }
        )
        if turn.assistant_message or turn.error_message:
            messages.append(
                {
                    "role": "assistant",
                    "content": turn.assistant_message,
                    "turn_id": turn.turn_id,
                    "turn_index": turn.turn_index,
                    "created_at": turn.created_at.isoformat(),
                    "error_message": turn.error_message,
                }
            )
    return messages


@router.get("")
async def list_sessions(
    request: Request,
    disease_profile_id: str | None = Query(default=None),
) -> dict[str, Any]:
    """列出已索引的会话，可按疾病档案筛选。"""
    memory_store = getattr(request.app.state, "memory_store", None)
    if memory_store is None:
        return {"sessions": [], "count": 0}

    sessions = await memory_store.list_agent_sessions(
        disease_profile_id=disease_profile_id,
        limit=50,
    )
    return {
        "sessions": [_serialise_session(session) for session in sessions],
        "count": len(sessions),
    }


@router.post("")
async def create_session(request: Request) -> dict[str, Any]:
    """创建新的对话会话并返回其 thread_id。"""
    thread_id = uuid.uuid4().hex
    LOGGER.info("Session created: %s", thread_id)
    return {"thread_id": thread_id}


@router.get("/{thread_id}")
async def get_session(thread_id: str, request: Request) -> dict[str, Any]:
    """获取会话元数据和消息历史。

    从检查点存储读取 thread_id 的检查点来重建迄今为止的对话。
    """
    memory_store = getattr(request.app.state, "memory_store", None)
    if memory_store is not None:
        indexed = await memory_store.get_agent_session(thread_id)
        if indexed is not None:
            turns = await memory_store.list_agent_turns(thread_id)
            messages = _flatten_turn_messages(turns)
            return {
                **_serialise_session(indexed),
                "thread_id": thread_id,
                "messages": messages,
                "turns": [_serialise_turn(turn) for turn in turns],
                "message_count": len(messages),
                "found": True,
            }

    graph = request.app.state.agent_graph
    config = {"configurable": {"thread_id": thread_id}}

    try:
        state = await graph.aget_state(config)
        if state is None or state.values is None:
            return {"thread_id": thread_id, "messages": [], "found": False}

        messages = state.values.get("messages", [])
        serialised = []
        for msg in messages:
            serialised.append(
                {
                    "role": getattr(msg, "type", "unknown"),
                    "content": str(getattr(msg, "content", "")),
                }
            )

        return {
            "thread_id": thread_id,
            "messages": serialised,
            "message_count": len(serialised),
            "found": True,
        }
    except Exception as exc:
        LOGGER.warning("Failed to load session %s: %s", thread_id, exc)
        return {"thread_id": thread_id, "messages": [], "found": False}


@router.patch("/{thread_id}")
async def update_session(thread_id: str, body: SessionUpdateRequest, request: Request) -> dict[str, Any]:
    """更新会话元数据（如标题）。"""
    memory_store = getattr(request.app.state, "memory_store", None)
    if memory_store is None:
        return {"thread_id": thread_id, "updated": False, "error": "memory store unavailable"}

    await memory_store.update_agent_session_title(thread_id, body.title)
    LOGGER.info("Session renamed: %s -> %s", thread_id, body.title)
    return {"thread_id": thread_id, "title": body.title, "updated": True}


@router.delete("/{thread_id}")
async def delete_session(thread_id: str, request: Request) -> dict[str, Any]:
    """删除对话会话。

    注意：完整的检查点删除取决于检查点存储的实现。对于 SQLite，
    这是一个尽力而为的操作。
    """
    LOGGER.info("Session delete requested: %s", thread_id)
    memory_store = getattr(request.app.state, "memory_store", None)
    if memory_store is not None:
        try:
            await memory_store.delete_agent_session(thread_id)
        except Exception as exc:
            LOGGER.warning("Failed to purge indexed session %s: %s", thread_id, exc)
    # LangGraph 的检查点存储尚未暴露标准删除 API。
    # 目前我们确认请求；实际清除可通过后台任务或直接数据库操作完成。
    return {"thread_id": thread_id, "deleted": True}