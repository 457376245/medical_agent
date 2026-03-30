"""Task-related data models.

Pydantic models for the MQ task processing pipeline (parse / generate).
Migrated from inline definitions in ``main.py``.
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class TaskPayload(BaseModel):
    """Envelope for MQ task messages and ``/internal/*`` HTTP requests."""

    payload: dict[str, Any] = Field(
        ..., description="Task-specific payload forwarded to the worker."
    )
