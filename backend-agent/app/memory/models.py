"""记忆数据模型。"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Literal

from pydantic import BaseModel, Field


class AgentTraceEvent(BaseModel):
    """一次助手轮次的单个持久化 Agent 追踪事件。"""

    event: Literal["tool_call", "tool_result", "error"]
    tool: str | None = None
    data: dict[str, Any] = Field(default_factory=dict)
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class AgentSessionRecord(BaseModel):
    """Agent 工作台侧边栏使用的会话索引行。"""

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
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    updated_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    turn_count: int = 0


class AgentSessionTurn(BaseModel):
    """带有追踪元数据的持久化用户/助手轮次。"""

    turn_id: str | None = None
    thread_id: str
    turn_index: int
    user_message: str
    assistant_message: str = ""
    trace_events: list[AgentTraceEvent] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)
    error_message: str | None = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))