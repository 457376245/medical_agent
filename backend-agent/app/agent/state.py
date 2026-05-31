"""Agent runtime 状态定义。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from app.agent.messages import AgentMessage


@dataclass
class AgentRuntimeState:
    """每个 thread_id 持久化的轻量 Agent 状态。"""

    thread_id: str
    messages: list[AgentMessage] = field(default_factory=list)
    active_context_signature: str | None = None
    active_context_bundle: dict[str, Any] | None = None
    active_context_status: str | None = None

    def model_dump(self) -> dict[str, Any]:
        return {
            "thread_id": self.thread_id,
            "messages": [message.model_dump() for message in self.messages],
            "active_context_signature": self.active_context_signature,
            "active_context_bundle": self.active_context_bundle,
            "active_context_status": self.active_context_status,
        }

    @classmethod
    def model_validate(cls, value: dict[str, Any]) -> "AgentRuntimeState":
        return cls(
            thread_id=str(value.get("thread_id") or ""),
            messages=[
                AgentMessage.model_validate(item)
                for item in value.get("messages", [])
                if isinstance(item, dict)
            ],
            active_context_signature=value.get("active_context_signature"),
            active_context_bundle=value.get("active_context_bundle")
            if isinstance(value.get("active_context_bundle"), dict)
            else None,
            active_context_status=value.get("active_context_status"),
        )
