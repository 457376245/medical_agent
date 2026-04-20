"""聊天相关数据模型。"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    """聊天端点的传入用户消息。"""

    thread_id: str | None = Field(
        default=None,
        description="要恢复的现有会话 ID。省略则开始新会话。",
    )
    message: str = Field(..., min_length=1, description="用户消息内容。")
    metadata: dict[str, Any] = Field(
        default_factory=dict,
        description="可选元数据（patient_id、scenario、workflow、urgency_level、audience 等）。",
    )
