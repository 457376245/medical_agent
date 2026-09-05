"""Agent runtime 状态定义。"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

from app.agent.messages import AgentMessage


@dataclass
class AgentRuntimeState:
    """每个 thread_id 持久化的轻量 Agent 状态。"""

    thread_id: str
    owner_key: str
    messages: list[AgentMessage] = field(default_factory=list)
    active_context_signature: str | None = None
    active_context_bundle: dict[str, Any] | None = None
    active_context_status: str | None = None
    active_context_revision: str | None = None
    context_generated_at: datetime | None = None
    context_fetched_at: datetime | None = None
    last_diagnostics: dict[str, Any] = field(default_factory=dict)

    def model_dump(self) -> dict[str, Any]:
        return {
            "thread_id": self.thread_id,
            "owner_key": self.owner_key,
            "messages": [message.model_dump() for message in self.messages],
            "active_context_signature": self.active_context_signature,
            "active_context_bundle": self.active_context_bundle,
            "active_context_status": self.active_context_status,
            "active_context_revision": self.active_context_revision,
            "context_generated_at": self.context_generated_at.isoformat() if self.context_generated_at else None,
            "context_fetched_at": self.context_fetched_at.isoformat() if self.context_fetched_at else None,
            "last_diagnostics": self.last_diagnostics,
        }

    @classmethod
    def model_validate(cls, value: dict[str, Any]) -> "AgentRuntimeState":
        return cls(
            thread_id=str(value.get("thread_id") or ""),
            owner_key=str(value.get("owner_key") or ""),
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
            active_context_revision=value.get("active_context_revision"),
            context_generated_at=_parse_datetime(value.get("context_generated_at")),
            context_fetched_at=_parse_datetime(value.get("context_fetched_at")),
            last_diagnostics=value.get("last_diagnostics")
            if isinstance(value.get("last_diagnostics"), dict)
            else {},
        )


def _parse_datetime(value: Any) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value))
    except ValueError:
        return None
