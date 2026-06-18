"""OpenAI Agents SDK runtime adapter."""

from __future__ import annotations

import json
import logging
from collections.abc import AsyncGenerator, Callable
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Protocol

from agents import Agent, ModelSettings, Runner
from agents.extensions.memory.async_sqlite_session import AsyncSQLiteSession

from app.agent.context import context_signature_from_metadata, parse_context_bundle
from app.agent.events import AgentStreamEvent
from app.agent.messages import AgentMessage, AgentToolCall
from app.agent.prompting import build_agent_instructions
from app.agent.state import AgentRuntimeState
from app.agent.tool_runner import execute_tool_call, tool_map
from app.config import (
    AGENT_SESSION_DB_PATH,
    DEFAULT_AGENT_MAX_TOKENS,
    DEFAULT_AGENT_MODEL,
    DEFAULT_AGENT_TEMPERATURE,
    MAX_TOOL_ROUNDS,
)
from app.ids import new_prefixed_ordered_id
from app.tools.registry import ToolSpec, get_model_tools, get_tools, to_agents_tools

LOGGER = logging.getLogger(__name__)
CONTEXT_TOOL_NAME = "fetch_disease_profile_context"


class AgentRuntimeStore(Protocol):
    async def get_agent_runtime_state(self, thread_id: str) -> AgentRuntimeState | None: ...

    async def upsert_agent_runtime_state(self, state: AgentRuntimeState) -> None: ...


@dataclass
class AgentRunContext:
    """Per-run state passed to Agents SDK tools."""

    failed_tool_keys: set[tuple[str, str]] = field(default_factory=set)


class AgentRuntime:
    """Agent runtime backed by OpenAI Agents SDK."""

    def __init__(
        self,
        *,
        state_store: AgentRuntimeStore | None = None,
        model_tools: list[ToolSpec] | None = None,
        all_tools: list[ToolSpec] | None = None,
        runner: Any = Runner,
        session_factory: Callable[[str], Any] | None = None,
    ) -> None:
        self._state_store = state_store
        self._runner = runner
        self._model_tools = model_tools if model_tools is not None else get_model_tools()
        self._agent_tools = to_agents_tools(self._model_tools)
        self._tools_by_name = tool_map(all_tools if all_tools is not None else get_tools())
        self._session_factory = session_factory or self._create_session

    async def get_state(self, thread_id: str) -> AgentRuntimeState | None:
        if self._state_store is None:
            return None
        return await self._state_store.get_agent_runtime_state(thread_id)

    async def get_session_items(self, thread_id: str) -> list[dict[str, Any]]:
        session = self._session_factory(thread_id)
        try:
            items = await session.get_items()
            return [item for item in items if isinstance(item, dict)]
        finally:
            close = getattr(session, "close", None)
            if close is not None:
                await close()

    async def clear_session(self, thread_id: str) -> None:
        session = self._session_factory(thread_id)
        try:
            await session.clear_session()
        finally:
            close = getattr(session, "close", None)
            if close is not None:
                await close()

    async def stream(
        self,
        *,
        thread_id: str,
        user_message: str,
        metadata: dict[str, Any],
    ) -> AsyncGenerator[AgentStreamEvent, None]:
        state = await self._load_state(thread_id)
        await self._preload_context_if_needed(state, metadata)

        instructions = build_agent_instructions(
            state={
                "metadata": metadata,
                "active_context_bundle": state.active_context_bundle,
                "active_context_status": state.active_context_status,
            }
        )
        agent = Agent[AgentRunContext](
            name="medical-agent",
            instructions=instructions,
            model=DEFAULT_AGENT_MODEL,
            model_settings=ModelSettings(
                temperature=DEFAULT_AGENT_TEMPERATURE,
                max_tokens=DEFAULT_AGENT_MAX_TOKENS,
                truncation="auto",
            ),
            tools=self._agent_tools,
        )

        session = self._session_factory(thread_id)
        result = self._runner.run_streamed(
            agent,
            input=user_message,
            context=AgentRunContext(),
            max_turns=MAX_TOOL_ROUNDS,
            session=session,
        )

        try:
            async for event in result.stream_events():
                mapped = _map_stream_event(event)
                if mapped is not None:
                    yield mapped
            run_exception = getattr(result, "run_loop_exception", None)
            if run_exception is not None:
                raise run_exception
            await self._save_state(state)
        finally:
            close = getattr(session, "close", None)
            if close is not None:
                await close()

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

    def _create_session(self, thread_id: str) -> AsyncSQLiteSession:
        db_path = Path(AGENT_SESSION_DB_PATH)
        if not db_path.is_absolute():
            db_path = Path.cwd() / db_path
        db_path.parent.mkdir(parents=True, exist_ok=True)
        return AsyncSQLiteSession(thread_id, db_path=db_path)


def _map_stream_event(event: Any) -> AgentStreamEvent | None:
    event_type = getattr(event, "type", "")
    if event_type == "raw_response_event":
        return _map_raw_response_event(getattr(event, "data", None))
    if event_type == "run_item_stream_event":
        return _map_run_item_event(event)
    return None


def _map_raw_response_event(data: Any) -> AgentStreamEvent | None:
    if getattr(data, "type", "") != "response.output_text.delta":
        return None
    delta = getattr(data, "delta", "")
    if not delta:
        return None
    return AgentStreamEvent(type="token", content=str(delta))


def _map_run_item_event(event: Any) -> AgentStreamEvent | None:
    name = str(getattr(event, "name", "") or "")
    item = getattr(event, "item", None)
    if name == "tool_called":
        tool_name, tool_input = _tool_call_payload(getattr(item, "raw_item", None))
        return AgentStreamEvent(
            type="tool_call",
            tool=tool_name or "unknown",
            data={"input": tool_input},
        )
    if name == "tool_output":
        tool_name, _ = _tool_call_payload(getattr(item, "raw_item", None))
        return AgentStreamEvent(
            type="tool_result",
            tool=tool_name or "unknown",
            data={"output": str(getattr(item, "output", "") or "")},
        )
    return None


def _tool_call_payload(raw_item: Any) -> tuple[str, dict[str, Any]]:
    name = str(getattr(raw_item, "name", "") or "")
    arguments = getattr(raw_item, "arguments", None)
    if arguments is None and isinstance(raw_item, dict):
        name = str(raw_item.get("name") or "")
        arguments = raw_item.get("arguments")
    if isinstance(arguments, dict):
        return name, arguments
    try:
        parsed = json.loads(str(arguments or "{}"))
    except json.JSONDecodeError:
        parsed = {}
    return name, parsed if isinstance(parsed, dict) else {}
