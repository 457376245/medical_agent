"""Memory data models.

Pydantic models for memory entities: conversation summaries, patient
context snapshots, and extracted medical facts.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

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
