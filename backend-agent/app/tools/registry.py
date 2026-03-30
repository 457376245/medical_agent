"""Tool registry.

Central registry for all Agent-callable tools.  Supports listing all
available tools or filtering by scenario/role.
"""

from __future__ import annotations

from langchain_core.tools import BaseTool

from app.tools.document_parse import parse_document
from app.tools.text_generate import generate_medical_text

# ---------------------------------------------------------------------------
# Tool groups — each scenario may use a different subset
# ---------------------------------------------------------------------------

ALL_TOOLS: list[BaseTool] = [
    parse_document,
    generate_medical_text,
]

# Predefined subsets for common scenarios
CONSULTATION_TOOLS: list[BaseTool] = [
    parse_document,
    generate_medical_text,
]

REPORT_TOOLS: list[BaseTool] = [
    parse_document,
]


def get_tools(scenario: str | None = None) -> list[BaseTool]:
    """Return the tool list for the given scenario.

    Args:
        scenario: Optional scenario name.  If ``None`` or unrecognised,
                  returns all available tools.

    Returns:
        List of LangChain ``BaseTool`` instances.
    """
    mapping: dict[str, list[BaseTool]] = {
        "consultation": CONSULTATION_TOOLS,
        "report": REPORT_TOOLS,
    }
    if scenario is not None and scenario.lower() in mapping:
        return mapping[scenario.lower()]
    return list(ALL_TOOLS)
