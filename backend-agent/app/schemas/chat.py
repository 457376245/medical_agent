"""聊天相关数据模型。

SSE 聊天接口的 Pydantic 模型：请求、事件和会话元数据。
"""

from __future__ import annotations

from datetime import datetime
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
        description="可选元数据（patient_id、scenario 等）。",
    )


class ChatEvent(BaseModel):
    """发送给客户端的单个 SSE 事件。

    事件类型：
    - ``token``：增量文本块
    - ``tool_call``：工具调用通知
    - ``tool_result``：工具执行结果
    - ``done``：包含完整响应的最终事件
    - ``error``：错误通知
    """

    event: str
    data: dict[str, Any] = Field(default_factory=dict)


class SessionInfo(BaseModel):
    """对话会话的摘要。"""

    thread_id: str
    created_at: datetime
    updated_at: datetime
    message_count: int = 0
    title: str | None = None