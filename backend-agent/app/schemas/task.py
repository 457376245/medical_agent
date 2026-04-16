"""任务相关数据模型。

MQ 任务处理管道（解析/生成）的 Pydantic 模型。
从 main.py 中的内联定义迁移而来。
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class TaskPayload(BaseModel):
    """MQ 任务消息和 /internal/* HTTP 请求的信封。"""

    payload: dict[str, Any] = Field(
        ..., description="转发给 worker 的任务特定 payload。"
    )