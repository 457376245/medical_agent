"""OpenAI Agents SDK runtime adapter."""

from __future__ import annotations

import json
import logging
import hashlib
from collections.abc import AsyncGenerator, Callable
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Protocol

from agents import Agent, ModelSettings, RunConfig, Runner, SessionSettings
from agents.run_config import ModelInputData
from agents.extensions.memory.async_sqlite_session import AsyncSQLiteSession

from app.agent.context import context_signature_from_metadata, parse_context_bundle
from app.agent.events import AgentStreamEvent
from app.agent.prompting import AGENT_PROMPT_VERSION, build_agent_instructions, build_untrusted_context
from app.agent.state import AgentRuntimeState
from app.agent.tool_runner import tool_map
from app.auth import AgentScope
from app.config import (
    AGENT_SESSION_DB_PATH,
    AGENT_CONTEXT_CACHE_TTL_SECONDS,
    AGENT_CONTEXT_MAX_CHARS,
    AGENT_HISTORY_MAX_ITEMS,
    AGENT_HISTORY_MAX_TOKENS,
    DEFAULT_AGENT_MAX_TOKENS,
    DEFAULT_AGENT_MODEL,
    DEFAULT_AGENT_TEMPERATURE,
    MAX_TOOL_ROUNDS,
)
from app.tools.registry import ToolSpec, get_model_tools, get_tools, to_agents_tools

LOGGER = logging.getLogger(__name__)
CONTEXT_TOOL_NAME = "fetch_disease_profile_context"


class AgentRuntimeStore(Protocol):
    async def get_agent_runtime_state(self, thread_id: str, owner_key: str) -> AgentRuntimeState | None: ...

    async def upsert_agent_runtime_state(self, state: AgentRuntimeState) -> None: ...


@dataclass
class AgentRunContext:
    """Per-run state passed to Agents SDK tools."""

    failed_tool_keys: set[tuple[str, str]] = field(default_factory=set)
    allowed_attachment_keys: frozenset[str] = frozenset()
    untrusted_context: str | None = None
    diagnostics: dict[str, Any] = field(default_factory=dict)


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

    async def get_state(self, thread_id: str, owner_key: str) -> AgentRuntimeState | None:
        if self._state_store is None:
            return None
        return await self._state_store.get_agent_runtime_state(thread_id, owner_key)

    async def get_session_items(self, thread_id: str, owner_key: str) -> list[dict[str, Any]]:
        session = self._session_factory(self._session_id(thread_id, owner_key))
        try:
            items = await session.get_items()
            return [item for item in items if isinstance(item, dict)]
        finally:
            close = getattr(session, "close", None)
            if close is not None:
                await close()

    async def clear_session(self, thread_id: str, owner_key: str) -> None:
        session = self._session_factory(self._session_id(thread_id, owner_key))
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
        scope: AgentScope,
    ) -> AsyncGenerator[AgentStreamEvent, None]:
        state = await self._load_state(thread_id, scope.owner_key)
        await self._preload_context_if_needed(state, metadata, scope)

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

        untrusted_context = build_untrusted_context(
            state={
                "metadata": metadata,
                "active_context_bundle": state.active_context_bundle,
                "active_context_status": state.active_context_status,
            }
        )
        if untrusted_context and len(untrusted_context) > AGENT_CONTEXT_MAX_CHARS:
            untrusted_context = untrusted_context[:AGENT_CONTEXT_MAX_CHARS] + "…"
        run_context = AgentRunContext(
            allowed_attachment_keys=frozenset(_attachment_keys(metadata)),
            untrusted_context=untrusted_context,
            diagnostics=_initial_diagnostics(state, untrusted_context),
        )
        session = self._session_factory(self._session_id(thread_id, scope.owner_key))
        run_config = RunConfig(
            workflow_name="medical-agent-chat",
            group_id=_safe_group_id(thread_id, scope.owner_key),
            trace_include_sensitive_data=False,
            trace_metadata={
                "prompt_version": AGENT_PROMPT_VERSION,
                "model": DEFAULT_AGENT_MODEL,
                "context_status": state.active_context_status or "none",
                "context_revision": state.active_context_revision or "none",
            },
            session_settings=SessionSettings(limit=AGENT_HISTORY_MAX_ITEMS),
            session_input_callback=_history_callback(run_context),
            call_model_input_filter=_model_input_filter,
        )
        result = self._runner.run_streamed(
            agent,
            input=user_message,
            context=run_context,
            max_turns=MAX_TOOL_ROUNDS,
            session=session,
            run_config=run_config,
        )

        try:
            async for event in result.stream_events():
                mapped = _map_stream_event(event)
                if mapped is not None:
                    yield mapped
            run_exception = getattr(result, "run_loop_exception", None)
            if run_exception is not None:
                raise run_exception
            run_context.diagnostics["usage"] = _usage_diagnostics(result)
            state.last_diagnostics = run_context.diagnostics
            await self._save_state(state)
        finally:
            close = getattr(session, "close", None)
            if close is not None:
                await close()

    async def _load_state(self, thread_id: str, owner_key: str) -> AgentRuntimeState:
        if self._state_store is not None:
            state = await self._state_store.get_agent_runtime_state(thread_id, owner_key)
            if state is not None:
                return state
        return AgentRuntimeState(thread_id=thread_id, owner_key=owner_key)

    async def _save_state(self, state: AgentRuntimeState) -> None:
        if self._state_store is not None:
            await self._state_store.upsert_agent_runtime_state(state)

    async def _preload_context_if_needed(
        self,
        state: AgentRuntimeState,
        metadata: dict[str, Any],
        scope: AgentScope,
    ) -> None:
        next_signature = context_signature_from_metadata(metadata)
        if not next_signature:
            state.active_context_signature = None
            state.active_context_bundle = None
            state.active_context_status = None
            return
        now = datetime.now(timezone.utc)
        age_seconds = (
            max(0.0, (now - state.context_fetched_at).total_seconds())
            if state.context_fetched_at
            else None
        )
        cache_hit = (
            state.active_context_status in {"ready", "partial"}
            and next_signature == (state.active_context_signature or "")
            and AGENT_CONTEXT_CACHE_TTL_SECONDS > 0
            and age_seconds is not None
            and age_seconds < AGENT_CONTEXT_CACHE_TTL_SECONDS
        )
        if cache_hit:
            state.last_diagnostics = {
                "context": {
                    "status": state.active_context_status,
                    "revision": state.active_context_revision,
                    "age_seconds": round(age_seconds or 0.0, 3),
                    "cache": "hit",
                    "retry": False,
                }
            }
            return

        call_args: dict[str, Any] = {
            "disease_profile_id": str(metadata.get("disease_profile_id") or "").strip()
        }
        record_id = str(metadata.get("record_id") or "").strip()
        if record_id:
            call_args["record_id"] = record_id
        call_args["scope"] = scope
        context_tool = self._tools_by_name.get(CONTEXT_TOOL_NAME)
        if context_tool is None:
            bundle = None
        else:
            import asyncio

            raw_bundle = await asyncio.to_thread(context_tool.handler, **call_args)
            bundle = parse_context_bundle(str(raw_bundle))
        status = "unavailable"
        if isinstance(bundle, dict):
            raw_status = str(bundle.get("context_status") or "unavailable").strip().lower()
            status = raw_status if raw_status in {"ready", "partial", "unavailable"} else "unavailable"
        state.active_context_signature = next_signature if status != "unavailable" else None
        state.active_context_status = status
        state.active_context_bundle = bundle if status != "unavailable" else None
        state.active_context_revision = (
            str(bundle.get("context_revision") or "").strip() or None
            if isinstance(bundle, dict) and status != "unavailable"
            else None
        )
        state.context_generated_at = _parse_datetime(bundle.get("generated_at")) if isinstance(bundle, dict) else None
        state.context_fetched_at = now
        state.last_diagnostics = {
            "context": {
                "status": status,
                "revision": state.active_context_revision,
                "age_seconds": 0.0,
                "cache": "miss",
                "retry": state.active_context_status == "unavailable" or state.active_context_signature is None,
            }
        }

    def _create_session(self, thread_id: str) -> AsyncSQLiteSession:
        db_path = Path(AGENT_SESSION_DB_PATH)
        if not db_path.is_absolute():
            db_path = Path.cwd() / db_path
        db_path.parent.mkdir(parents=True, exist_ok=True)
        return AsyncSQLiteSession(thread_id, db_path=db_path)

    def _session_id(self, thread_id: str, owner_key: str) -> str:
        return hashlib.sha256(f"{owner_key}:{thread_id}".encode("utf-8")).hexdigest()


def _attachment_keys(metadata: dict[str, Any]) -> list[str]:
    attachments = metadata.get("attachments")
    if not isinstance(attachments, list):
        return []
    return [
        str(item.get("object_key") or "").strip()
        for item in attachments
        if isinstance(item, dict) and str(item.get("object_key") or "").strip()
    ]


def _safe_group_id(thread_id: str, owner_key: str) -> str:
    return hashlib.sha256(f"{owner_key}:{thread_id}".encode("utf-8")).hexdigest()[:24]


def _history_callback(run_context: AgentRunContext) -> Callable[[list[Any], list[Any]], list[Any]]:
    def select(history: list[Any], new_items: list[Any]) -> list[Any]:
        non_tool_history = [item for item in history if not _is_tool_item(item)]
        dropped_tools = len(history) - len(non_tool_history)
        kept: list[Any] = []
        used = 0
        for item in reversed(non_tool_history):
            size = _conservative_token_estimate(item)
            if used + size > AGENT_HISTORY_MAX_TOKENS:
                continue
            kept.append(item)
            used += size
        kept.reverse()
        while kept and str(kept[0].get("role") or "").lower() != "user":
            kept.pop(0)
        run_context.diagnostics["history"] = {
            "session_items": len(history),
            "selected_items": len(kept),
            "trimmed_items": len(history) - len(kept),
            "tool_items_dropped": dropped_tools,
            "budget_tokens": AGENT_HISTORY_MAX_TOKENS,
            "token_estimate": "utf8_bytes_upper_bound",
            "compacted": False,
        }
        return [*kept, *new_items]

    return select


def _is_tool_item(item: Any) -> bool:
    if not isinstance(item, dict):
        return False
    item_type = str(item.get("type") or "").lower()
    role = str(item.get("role") or "").lower()
    return role == "tool" or "function_call" in item_type or "tool_call" in item_type


def _conservative_token_estimate(item: Any) -> int:
    rendered = json.dumps(item, ensure_ascii=False, default=str, separators=(",", ":"))
    return len(rendered.encode("utf-8"))


def _model_input_filter(data: Any) -> ModelInputData:
    run_context = data.context
    model_data = data.model_data
    if not isinstance(run_context, AgentRunContext) or not run_context.untrusted_context:
        return model_data
    item = {"role": "user", "content": run_context.untrusted_context}
    inputs = list(model_data.input)
    insert_at = max(0, len(inputs) - 1)
    inputs.insert(insert_at, item)
    return ModelInputData(input=inputs, instructions=model_data.instructions)


def _initial_diagnostics(state: AgentRuntimeState, context_text: str | None) -> dict[str, Any]:
    context = dict(state.last_diagnostics.get("context") or {})
    if state.context_fetched_at:
        context["age_seconds"] = round(
            max(0.0, (datetime.now(timezone.utc) - state.context_fetched_at).total_seconds()),
            3,
        )
    return {
        "model": DEFAULT_AGENT_MODEL,
        "prompt_version": AGENT_PROMPT_VERSION,
        "context_version": "context-v2",
        "evaluator_version": "grounded-evaluator-v2",
        "context": context,
        "context_chars": len(context_text or ""),
        "history": {
            "session_items": 0,
            "selected_items": 0,
            "trimmed_items": 0,
            "tool_items_dropped": 0,
            "budget_tokens": AGENT_HISTORY_MAX_TOKENS,
            "token_estimate": "utf8_bytes_upper_bound",
            "compacted": False,
        },
        "tools": [],
    }


def _usage_diagnostics(result: Any) -> dict[str, int]:
    usage = getattr(getattr(result, "context_wrapper", None), "usage", None)
    details = getattr(usage, "input_tokens_details", None)
    return {
        "requests": int(getattr(usage, "requests", 0) or 0),
        "input_tokens": int(getattr(usage, "input_tokens", 0) or 0),
        "output_tokens": int(getattr(usage, "output_tokens", 0) or 0),
        "cached_tokens": int(getattr(details, "cached_tokens", 0) or 0),
    }


def _parse_datetime(value: Any) -> datetime | None:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)
    except ValueError:
        return None


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
