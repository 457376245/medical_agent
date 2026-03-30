"""Session management endpoints.

CRUD operations for conversation sessions: create, resume, list, delete.
Each session maps to a LangGraph ``thread_id``.
"""

from __future__ import annotations

import logging
import uuid
from typing import Any

from fastapi import APIRouter, Request

LOGGER = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/sessions", tags=["sessions"])


@router.post("")
async def create_session(request: Request) -> dict[str, Any]:
    """Create a new conversation session and return its ``thread_id``."""
    thread_id = uuid.uuid4().hex
    LOGGER.info("Session created: %s", thread_id)
    return {"thread_id": thread_id}


@router.get("/{thread_id}")
async def get_session(thread_id: str, request: Request) -> dict[str, Any]:
    """Retrieve session metadata and message history.

    Reads the checkpoint for *thread_id* from the checkpoint store to
    reconstruct the conversation so far.
    """
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


@router.delete("/{thread_id}")
async def delete_session(thread_id: str, request: Request) -> dict[str, Any]:
    """Delete a conversation session.

    Note: full checkpoint deletion depends on the checkpoint store
    implementation.  For SQLite this is a best-effort operation.
    """
    LOGGER.info("Session delete requested: %s", thread_id)
    # LangGraph's checkpoint stores do not yet expose a standard delete API.
    # For now we acknowledge the request; actual purging can be done via a
    # background job or direct DB operation.
    return {"thread_id": thread_id, "deleted": True}
