"""轻量 Agent runtime。"""

from __future__ import annotations

import json
import logging
import os
from collections.abc import AsyncGenerator
from typing import Any, Protocol

from openai import AsyncOpenAI

from app.agent.context import context_signature_from_metadata, parse_context_bundle
from app.agent.events import AgentStreamEvent
from app.agent.messages import AgentMessage, AgentToolCall
from app.agent.prompting import build_prompt_messages
from app.agent.state import AgentRuntimeState
from app.agent.tool_runner import execute_tool_call, split_allowed_tool_calls, tool_map
from app.config import (
    CONVERSATION_WINDOW_MAX_TOKENS,
    DEFAULT_AGENT_MAX_TOKENS,
    DEFAULT_AGENT_MODEL,
    DEFAULT_AGENT_TEMPERATURE,
    MAX_TOOL_ROUNDS,
    OPENAI_API_KEY,
    OPENAI_BASE_URL,
    OPENAI_REQUEST_TIMEOUT_SECONDS,
    OPENAI_SDK_RETRIES,
)
from app.ids import new_prefixed_ordered_id
from app.tools.registry import ToolSpec, get_model_tools, get_tools
from app.utils import normalize_openai_base_url

LOGGER = logging.getLogger(__name__)
CONTEXT_TOOL_NAME = "fetch_disease_profile_context"


class AgentRuntimeStore(Protocol):
    async def get_agent_runtime_state(self, thread_id: str) -> AgentRuntimeState | None: ...

    async def upsert_agent_runtime_state(self, state: AgentRuntimeState) -> None: ...


class AgentRuntime:
    """显式 Agent 运行循环。"""

    def __init__(
        self,
        *,
        state_store: AgentRuntimeStore | None = None,
        client: AsyncOpenAI | None = None,
        model_tools: list[ToolSpec] | None = None,
        all_tools: list[ToolSpec] | None = None,
    ) -> None:
        api_key = os.getenv("OPENAI_API_KEY", "").strip() or OPENAI_API_KEY
        base_url = normalize_openai_base_url(
            os.getenv("OPENAI_BASE_URL", "").strip() or OPENAI_BASE_URL
        )
        if client is None:
            if not api_key:
                raise RuntimeError("OPENAI_API_KEY 未配置")
            if not base_url:
                raise RuntimeError("OPENAI_BASE_URL 未配置")
            client = AsyncOpenAI(
                api_key=api_key,
                base_url=base_url,
                timeout=OPENAI_REQUEST_TIMEOUT_SECONDS,
                max_retries=OPENAI_SDK_RETRIES,
            )
        self._client = client
        self._state_store = state_store
        self._model_tools = model_tools if model_tools is not None else get_model_tools()
        self._tools_by_name = tool_map(all_tools if all_tools is not None else get_tools())

    async def get_state(self, thread_id: str) -> AgentRuntimeState | None:
        if self._state_store is None:
            return None
        return await self._state_store.get_agent_runtime_state(thread_id)

    async def stream(
        self,
        *,
        thread_id: str,
        user_message: str,
        metadata: dict[str, Any],
    ) -> AsyncGenerator[AgentStreamEvent, None]:
        state = await self._load_state(thread_id)
        await self._preload_context_if_needed(state, metadata)
        state.messages.append(AgentMessage(role="user", content=user_message))

        tool_rounds = 0
        while True:
            assistant, token_parts = await self._stream_model_response(state, metadata)
            for token in token_parts:
                yield AgentStreamEvent(type="token", content=token)
            state.messages.append(assistant)

            if not assistant.tool_calls:
                break
            tool_rounds += 1
            if tool_rounds > MAX_TOOL_ROUNDS:
                LOGGER.warning("工具调用轮数已达上限 (%d)，强制结束", MAX_TOOL_ROUNDS)
                break

            allowed_calls, blocked_messages = split_allowed_tool_calls(
                state.messages[:-1],
                assistant.tool_calls,
            )
            for call in allowed_calls:
                yield AgentStreamEvent(
                    type="tool_call",
                    tool=call.name,
                    data={"input": call.args},
                )
                tool_message = await execute_tool_call(
                    call,
                    tools_by_name=self._tools_by_name,
                )
                state.messages.append(tool_message)
                yield AgentStreamEvent(
                    type="tool_result",
                    tool=call.name,
                    data={"output": tool_message.content},
                )

            for tool_message in blocked_messages:
                state.messages.append(tool_message)
                yield AgentStreamEvent(
                    type="tool_result",
                    tool=tool_message.name,
                    data={"output": tool_message.content},
                )

        await self._save_state(state)

    async def _load_state(self, thread_id: str) -> AgentRuntimeState:
        if self._state_store is not None:
            state = await self._state_store.get_agent_runtime_state(thread_id)
            if state is not None:
                return state
        return AgentRuntimeState(thread_id=thread_id)

    async def _save_state(self, state: AgentRuntimeState) -> None:
        if self._state_store is not None:
            await self._state_store.upsert_agent_runtime_state(state)

    async def _preload_context_if_needed(
        self,
        state: AgentRuntimeState,
        metadata: dict[str, Any],
    ) -> None:
        next_signature = context_signature_from_metadata(metadata)
        if not next_signature:
            state.active_context_signature = None
            state.active_context_bundle = None
            state.active_context_status = None
            return
        if next_signature == (state.active_context_signature or ""):
            return

        call_args: dict[str, Any] = {
            "disease_profile_id": str(metadata.get("disease_profile_id") or "").strip()
        }
        record_id = str(metadata.get("record_id") or "").strip()
        patient_id = str(metadata.get("patient_id") or "").strip()
        if record_id:
            call_args["record_id"] = record_id
        if patient_id:
            call_args["patient_id"] = patient_id

        call = AgentToolCall(
            id=new_prefixed_ordered_id("context"),
            name=CONTEXT_TOOL_NAME,
            args=call_args,
        )
        state.messages.append(AgentMessage(role="assistant", tool_calls=[call]))
        tool_message = await execute_tool_call(call, tools_by_name=self._tools_by_name)
        state.messages.append(tool_message)

        bundle = parse_context_bundle(tool_message.content)
        status = "unavailable"
        if isinstance(bundle, dict):
            raw_status = str(bundle.get("context_status") or "unavailable").strip().lower()
            status = raw_status if raw_status in {"ready", "partial", "unavailable"} else "unavailable"
        state.active_context_signature = next_signature
        state.active_context_status = status
        state.active_context_bundle = bundle if status != "unavailable" else None

    async def _stream_model_response(
        self,
        state: AgentRuntimeState,
        metadata: dict[str, Any],
    ) -> tuple[AgentMessage, list[str]]:
        prepared = build_prompt_messages(
            raw_messages=state.messages,
            state={
                "metadata": metadata,
                "active_context_bundle": state.active_context_bundle,
                "active_context_status": state.active_context_status,
            },
            max_tokens=CONVERSATION_WINDOW_MAX_TOKENS,
        ).messages

        request_payload: dict[str, Any] = {
            "model": DEFAULT_AGENT_MODEL,
            "messages": [message.to_openai() for message in prepared],
            "temperature": DEFAULT_AGENT_TEMPERATURE,
            "max_tokens": DEFAULT_AGENT_MAX_TOKENS,
            "stream": True,
        }
        if self._model_tools:
            request_payload["tools"] = [
                tool.to_openai_tool() for tool in self._model_tools
            ]
        response = await self._client.chat.completions.create(**request_payload)

        content_parts: list[str] = []
        tool_parts: dict[int, dict[str, Any]] = {}
        async for chunk in response:
            if not chunk.choices:
                continue
            delta = chunk.choices[0].delta
            content = getattr(delta, "content", None)
            if content:
                content_parts.append(str(content))
            for tool_call in getattr(delta, "tool_calls", None) or []:
                index = int(getattr(tool_call, "index", 0) or 0)
                item = tool_parts.setdefault(
                    index,
                    {"id": "", "name": "", "arguments": ""},
                )
                call_id = getattr(tool_call, "id", None)
                if call_id:
                    item["id"] = str(call_id)
                function = getattr(tool_call, "function", None)
                if function is None:
                    continue
                name = getattr(function, "name", None)
                if name:
                    item["name"] = str(name)
                arguments = getattr(function, "arguments", None)
                if arguments:
                    item["arguments"] += str(arguments)

        tool_calls = [
            AgentToolCall(
                id=item["id"] or new_prefixed_ordered_id("tool"),
                name=item["name"],
                args=_loads_tool_args(item["arguments"]),
            )
            for _, item in sorted(tool_parts.items())
            if item["name"]
        ]
        content = "".join(content_parts)
        if not content.strip() and not tool_calls:
            LOGGER.warning("LLM 返回空内容且无工具调用")
        return AgentMessage(role="assistant", content=content, tool_calls=tool_calls), content_parts


def _loads_tool_args(value: str) -> dict[str, Any]:
    try:
        parsed = json.loads(value or "{}")
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}
