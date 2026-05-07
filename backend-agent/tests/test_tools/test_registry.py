from __future__ import annotations

from app.tools.registry import get_model_tools, get_preload_tools, get_tools


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
