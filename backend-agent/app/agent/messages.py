"""Agent runtime 消息模型。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Literal

AgentRole = Literal["system", "user", "assistant", "tool"]


@dataclass(frozen=True)
class AgentToolCall:
    """模型请求执行的工具调用。"""

    id: str
    name: str
    args: dict[str, Any]

    def to_openai(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "type": "function",
            "function": {
                "name": self.name,
                "arguments": _json_dumps(self.args),
            },
        }


@dataclass(frozen=True)
class AgentMessage:
    """Agent 对话消息，避免业务代码依赖具体 Agent 框架。"""

    role: AgentRole
    content: str = ""
    tool_calls: list[AgentToolCall] = field(default_factory=list)
    tool_call_id: str | None = None
    name: str | None = None

    def to_openai(self) -> dict[str, Any]:
        if self.role == "tool":
            return {
                "role": "tool",
                "tool_call_id": self.tool_call_id or "",
                "content": self.content,
            }
        payload: dict[str, Any] = {
            "role": self.role,
            "content": self.content,
        }
        if self.tool_calls:
            payload["tool_calls"] = [call.to_openai() for call in self.tool_calls]
        return payload

    def model_dump(self) -> dict[str, Any]:
        return {
            "role": self.role,
            "content": self.content,
            "tool_calls": [
                {"id": call.id, "name": call.name, "args": call.args}
                for call in self.tool_calls
            ],
            "tool_call_id": self.tool_call_id,
            "name": self.name,
        }

    @classmethod
    def model_validate(cls, value: dict[str, Any]) -> "AgentMessage":
        tool_calls = [
            AgentToolCall(
                id=str(item.get("id") or ""),
                name=str(item.get("name") or ""),
                args=item.get("args") if isinstance(item.get("args"), dict) else {},
            )
            for item in value.get("tool_calls", [])
            if isinstance(item, dict)
        ]
        role = str(value.get("role") or "assistant")
        if role not in {"system", "user", "assistant", "tool"}:
            role = "assistant"
        return cls(
            role=role,  # type: ignore[arg-type]
            content=str(value.get("content") or ""),
            tool_calls=tool_calls,
            tool_call_id=value.get("tool_call_id"),
            name=value.get("name"),
        )


def _json_dumps(value: Any) -> str:
    import json

    try:
        return json.dumps(value or {}, ensure_ascii=False)
    except TypeError:
        return "{}"
