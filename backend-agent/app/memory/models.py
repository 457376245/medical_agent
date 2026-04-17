"""记忆数据模型。

记忆实体的 Pydantic 模型：对话摘要、患者上下文快照、提取的医疗事实，
以及 Agent 会话记录。
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field


class ConversationSummary(BaseModel):
    """对话会话的压缩摘要。"""

    thread_id: str
    summary: str
    key_topics: list[str] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=datetime.utcnow)


class PatientContext(BaseModel):
    """跨会话持久化的结构化患者上下文。"""

    patient_id: str
    thread_id: str
    demographics: dict[str, Any] = Field(default_factory=dict)
    diagnoses: list[str] = Field(default_factory=list)
    medications: list[str] = Field(default_factory=list)
    allergies: list[str] = Field(default_factory=list)
    updated_at: datetime = Field(default_factory=datetime.utcnow)


class MedicalFact(BaseModel):
    """对话期间提取的单个医疗事实。"""

    fact_id: str | None = None
    thread_id: str
    patient_id: str | None = None
    category: str  # 例如 "diagnosis"、"medication"、"lab_result"、"allergy"
    content: str
    source: str | None = None  # 哪条消息/工具产生了此事实
    confidence: float = Field(default=1.0, ge=0.0, le=1.0)
    created_at: datetime = Field(default_factory=datetime.utcnow)


class AgentTraceEvent(BaseModel):
    """一次助手轮次的单个持久化 Agent 追踪事件。"""

    event: Literal["tool_call", "tool_result", "error"]
    tool: str | None = None
    data: dict[str, Any] = Field(default_factory=dict)
    created_at: datetime = Field(default_factory=datetime.utcnow)


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
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)
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
    created_at: datetime = Field(default_factory=datetime.utcnow)