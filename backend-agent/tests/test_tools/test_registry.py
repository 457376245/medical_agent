from __future__ import annotations

import asyncio
from types import SimpleNamespace

from agents.tool_context import ToolContext

from app.tools.registry import ToolSpec, get_model_tools, get_preload_tools, get_tools, to_agents_tools


def _names(tools: list) -> set[str]:
    return {tool.name for tool in tools}


def test_context_tool_is_preload_only_by_default() -> None:
    assert "fetch_disease_profile_context" in _names(get_preload_tools())
    assert "fetch_disease_profile_context" not in _names(get_model_tools())


def test_model_tools_keep_document_and_generation_tools() -> None:
    names = _names(get_model_tools())

    assert "parse_document" in names
    assert "generate_medical_text" in names


def test_get_tools_returns_execution_superset() -> None:
    names = _names(get_tools())

    assert {"fetch_disease_profile_context", "parse_document", "generate_medical_text"} <= names


def test_agents_tool_invokes_handler_and_tracks_repeated_failures() -> None:
    calls = {"count": 0}

    def retry_me(value: str) -> str:
        calls["count"] += 1
        return "Error: failed" if value == "bad" else value

    tool = to_agents_tools(
        [
            ToolSpec(
                name="retry_me",
                description="测试工具",
                parameters={
                    "type": "object",
                    "properties": {"value": {"type": "string"}},
                    "required": ["value"],
                },
                handler=retry_me,
            )
        ]
    )[0]
    context = ToolContext(
        context=SimpleNamespace(failed_tool_keys=set()),
        tool_name="retry_me",
        tool_call_id="call-1",
        tool_arguments='{"value":"bad"}',
    )

    first = asyncio.run(tool.on_invoke_tool(context, '{"value":"bad"}'))
    second = asyncio.run(tool.on_invoke_tool(context, '{"value":"bad"}'))

    assert first == "Error: failed"
    assert second.startswith("Error:")
    assert "相同参数已失败" in second
    assert calls["count"] == 1


def test_parse_document_requires_authorized_attachment_key() -> None:
    calls = {"count": 0}

    def parse_document(object_key: str) -> str:
        calls["count"] += 1
        return object_key

    tool = to_agents_tools([
        ToolSpec(
            name="parse_document",
            description="parse",
            parameters={"type": "object", "properties": {"object_key": {"type": "string"}}},
            handler=parse_document,
        )
    ])[0]
    denied_context = ToolContext(
        context=SimpleNamespace(failed_tool_keys=set(), allowed_attachment_keys=frozenset({"allowed.pdf"}), diagnostics={}),
        tool_name="parse_document",
        tool_call_id="call-denied",
        tool_arguments='{"object_key":"other.pdf"}',
    )
    allowed_run_context = SimpleNamespace(failed_tool_keys=set(), allowed_attachment_keys=frozenset({"allowed.pdf"}), diagnostics={})
    allowed_context = ToolContext(
        context=allowed_run_context,
        tool_name="parse_document",
        tool_call_id="call-allowed",
        tool_arguments='{"object_key":"allowed.pdf"}',
    )

    denied = asyncio.run(tool.on_invoke_tool(denied_context, '{"object_key":"other.pdf"}'))
    allowed = asyncio.run(tool.on_invoke_tool(allowed_context, '{"object_key":"allowed.pdf"}'))

    assert denied.startswith("Error: 未授权")
    assert allowed == "allowed.pdf"
    assert calls["count"] == 1
    assert allowed_run_context.diagnostics["tools"][0]["status"] == "ok"
    assert "latency_ms" in allowed_run_context.diagnostics["tools"][0]
