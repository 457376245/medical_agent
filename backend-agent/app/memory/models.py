"""Memory data models.

Pydantic models for memory entities: conversation summaries, patient
context snapshots, extracted medical facts, and agent session records.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field


class ConversationSummary(BaseModel):
    """Compressed summary of a conversation session."""

    thread_id: str
    summary: str
    key_topics: list[str] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=datetime.utcnow)


class PatientContext(BaseModel):
    """Structured patient context persisted across sessions."""

    patient_id: str
    thread_id: str
    demographics: dict[str, Any] = Field(default_factory=dict)
    diagnoses: list[str] = Field(default_factory=list)
    medications: list[str] = Field(default_factory=list)
    allergies: list[str] = Field(default_factory=list)
    updated_at: datetime = Field(default_factory=datetime.utcnow)


class MedicalFact(BaseModel):
    """A single medical fact extracted during conversation."""

    fact_id: str | None = None
    thread_id: str
    patient_id: str | None = None
    category: str  # e.g. "diagnosis", "medication", "lab_result", "allergy"
    content: str
    source: str | None = None  # which message / tool produced this fact
    confidence: float = Field(default=1.0, ge=0.0, le=1.0)
    created_at: datetime = Field(default_factory=datetime.utcnow)


class AgentTraceEvent(BaseModel):
    """A single persisted agent trace event for one assistant turn."""

    event: Literal["tool_call", "tool_result", "error"]
    tool: str | None = None
    data: dict[str, Any] = Field(default_factory=dict)
    created_at: datetime = Field(default_factory=datetime.utcnow)


class AgentSessionRecord(BaseModel):
    """Session index row used by the agent workbench sidebar."""

    thread_id: str
    disease_profile_id: str | None = None
    disease_name: str | None = None
    record_id: str | None = None
    record_title: str | None = None
    record_date: str | None = None
    source_type: str | None = None
    context_signature: str | None = None
    context_status: str | None = None
    title: str | None = None
    last_user_message: str | None = None
    last_assistant_message: str | None = None
    last_message_preview: str | None = None
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)
    turn_count: int = 0


class AgentSessionTurn(BaseModel):
    """A persisted user/assistant turn with trace metadata."""

    turn_id: str | None = None
    thread_id: str
    turn_index: int
    user_message: str
    assistant_message: str = ""
    trace_events: list[AgentTraceEvent] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)
    error_message: str | None = None
    created_at: datetime = Field(default_factory=datetime.utcnow)
