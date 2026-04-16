"""工具注册中心。

所有 Agent 可调用工具的中央注册中心。支持列出所有可用工具
或按场景/角色筛选。
"""

from __future__ import annotations

from langchain_core.tools import BaseTool

from app.tools.disease_profile_context import fetch_disease_profile_context
from app.tools.document_parse import parse_document
from app.tools.text_generate import generate_medical_text

# ---------------------------------------------------------------------------
# 工具组 —— 每个场景可能使用不同的子集
# ---------------------------------------------------------------------------

ALL_TOOLS: list[BaseTool] = [
    fetch_disease_profile_context,
    parse_document,
    generate_medical_text,
]

# 常见场景的预定义子集
CONSULTATION_TOOLS: list[BaseTool] = [
    fetch_disease_profile_context,
    parse_document,
    generate_medical_text,
]

REPORT_TOOLS: list[BaseTool] = [
    parse_document,
]


def get_tools(scenario: str | None = None) -> list[BaseTool]:
    """返回给定场景的工具列表。

    Args:
        scenario: 可选场景名称。如果为 None 或未识别，返回所有可用工具。

    Returns:
        LangChain BaseTool 实例列表。
    """
    mapping: dict[str, list[BaseTool]] = {
        "consultation": CONSULTATION_TOOLS,
        "report": REPORT_TOOLS,
    }
    if scenario is not None and scenario.lower() in mapping:
        return mapping[scenario.lower()]
    return list(ALL_TOOLS)