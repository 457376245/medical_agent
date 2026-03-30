"""Tool: medical text generation.

Wraps ``providers/llm`` as an Agent-callable tool for generating medical
reports, summaries, and structured outputs.
"""

from __future__ import annotations

import json
import logging

from langchain_core.tools import tool

from app.providers.gateway import ProviderGateway

LOGGER = logging.getLogger(__name__)

# Module-level singleton — replaced by DI from main.py at startup.
_gateway: ProviderGateway | None = None


def configure(gateway: ProviderGateway) -> None:
    """Inject the gateway instance (called once at application startup)."""
    global _gateway  # noqa: PLW0603
    _gateway = gateway


@tool
def generate_medical_text(
    output_type: str = "SUMMARY",
    record_id: str = "",
    context: str = "{}",
) -> str:
    """Generate a medical text draft (summary, medication plan, or report analysis).

    Use this tool when the user asks to create, draft, or generate a
    medical document such as a clinical summary, medication plan, or
    report analysis.

    Args:
        output_type: One of "SUMMARY", "MED_PLAN", or "REPORT_ANALYSIS".
        record_id: The medical record identifier.
        context: JSON string of additional analysis context.

    Returns:
        Generated medical text content.
    """
    if _gateway is None:
        return "Error: text generation service is not configured."

    try:
        analysis_context = json.loads(context) if context else {}
    except (json.JSONDecodeError, TypeError):
        analysis_context = {}

    payload = {
        "type": output_type,
        "recordId": record_id,
        "analysisContext": analysis_context,
    }

    try:
        result = _gateway.execute_with_resilience("generate", payload)
        if result.success:
            return str(result.payload.get("content", ""))
        return f"Error: generation failed — {result.error_code}"
    except Exception as exc:
        LOGGER.warning("generate_medical_text tool failed: %s", exc, exc_info=True)
        return f"Error: generation failed — {exc}"
