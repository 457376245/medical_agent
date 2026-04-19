"""工具注册中心。"""

from __future__ import annotations

from langchain_core.tools import BaseTool

from app.tools.disease_profile_context import fetch_disease_profile_context
from app.tools.document_parse import parse_document
from app.tools.text_generate import generate_medical_text

ALL_TOOLS: list[BaseTool] = [
    fetch_disease_profile_context,
    parse_document,
    generate_medical_text,
]


def get_tools() -> list[BaseTool]:
    """返回所有可用工具。"""
    return list(ALL_TOOLS)