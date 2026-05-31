"""Agent runtime 对外事件。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Literal

AgentEventType = Literal["token", "tool_call", "tool_result"]


@dataclass(frozen=True)
class AgentStreamEvent:
    """轻量 runtime 产出的流式事件。"""

    type: AgentEventType
    content: str = ""
    tool: str | None = None
    data: dict[str, Any] = field(default_factory=dict)
