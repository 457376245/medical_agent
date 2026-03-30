"""Chat-related data models.

Pydantic models for the SSE chat interface: requests, events, and
session metadata.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    """Incoming user message for the chat endpoint."""

    thread_id: str | None = Field(
        default=None,
        description="Existing session ID to resume.  Omit to start a new session.",
    )
    message: str = Field(..., min_length=1, description="User message content.")
    metadata: dict[str, Any] = Field(
        default_factory=dict,
        description="Optional metadata (patient_id, scenario, etc.).",
    )


class ChatEvent(BaseModel):
    """A single SSE event sent back to the client.

    Event types:
    - ``token``: incremental text chunk
    - ``tool_call``: tool invocation notification
    - ``tool_result``: tool execution result
    - ``done``: final event with full response
    - ``error``: error notification
    """

    event: str
    data: dict[str, Any] = Field(default_factory=dict)


class SessionInfo(BaseModel):
    """Summary of a conversation session."""

    thread_id: str
    created_at: datetime
    updated_at: datetime
    message_count: int = 0
    title: str | None = None
