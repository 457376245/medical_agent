"""工具注册中心。"""

from __future__ import annotations

from langchain_core.tools import BaseTool

from app.tools.disease_profile_context import fetch_disease_profile_context
from app.tools.document_parse import parse_document
from app.tools.text_generate import generate_medical_text

PRELOAD_TOOLS: list[BaseTool] = [
    fetch_disease_profile_context,
]

MODEL_TOOLS: list[BaseTool] = [
    parse_document,
    generate_medical_text,
]

ALL_TOOLS: list[BaseTool] = [
    *PRELOAD_TOOLS,
    *MODEL_TOOLS,
]


def get_tools() -> list[BaseTool]:
    """返回所有可用工具。"""
    return list(ALL_TOOLS)


def get_preload_tools() -> list[BaseTool]:
    """返回系统预加载工具，不直接暴露给模型选择。"""
    return list(PRELOAD_TOOLS)


def get_model_tools(metadata: dict | None = None) -> list[BaseTool]:
    """返回模型可主动调用的工具。"""
    _ = metadata
    return list(MODEL_TOOLS)
