"""聊天相关数据模型。"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


Workflow = Literal["report_interpretation", "follow_up_prep", "medication_review"]
Scenario = Literal[
    "report_interpretation",
    "medication_review",
    "clinical_summary",
    "abnormal_reasoning",
]
Audience = Literal["patient", "caregiver", "clinician"]
UrgencyLevel = Literal["low", "medium", "watch", "warning", "high", "alert", "urgent"]


class AgentMetadata(BaseModel):
    """对话元数据。

    已知字段用于 prompt 策略分流；未知字段保留，兼容既有调用方。
    """

    model_config = ConfigDict(extra="allow")

    patient_id: str | None = None
    disease_profile_id: str | None = None
    disease_name: str | None = None
    record_id: str | None = None
    record_title: str | None = None
    record_date: str | None = None
    source_type: str | None = None
    workflow: Workflow | None = None
    scenario: Scenario | None = None
    audience: Audience | None = None
    urgency_level: UrgencyLevel | None = None
    context_signature: str | None = None
    context_status: str | None = None

    def to_graph_metadata(self) -> dict[str, Any]:
        """转换为 LangGraph 状态中使用的普通字典，保留未知字段。"""
        return self.model_dump(exclude_none=True)


class ChatAttachment(BaseModel):
    """用户随对话提供的可解析附件。"""

    object_key: str = Field(..., min_length=1)
    file_type: Literal["PDF", "IMAGE"] = "PDF"
    display_name: str | None = None


class ChatRequest(BaseModel):
    """聊天端点的传入用户消息。"""

    thread_id: str | None = Field(
        default=None,
        description="要恢复的现有会话 ID。省略则开始新会话。",
    )
    message: str = Field(..., min_length=1, description="用户消息内容。")
    metadata: AgentMetadata = Field(
        default_factory=AgentMetadata,
        description="可选元数据（patient_id、scenario、workflow、urgency_level、audience 等）。",
    )
    attachments: list[ChatAttachment] = Field(
        default_factory=list,
        description="可选附件列表，供 Agent 在需要时调用文档解析工具。",
    )
