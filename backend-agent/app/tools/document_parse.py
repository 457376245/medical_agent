"""Tool: document parsing.

Wraps the provider gateway as an Agent-callable tool and formats the
structured parse result into plain text for the conversation layer.
"""

from __future__ import annotations

import logging

from langchain_core.tools import tool

from app.providers.gateway import ProviderGateway

LOGGER = logging.getLogger(__name__)

_gateway: ProviderGateway | None = None


def configure(gateway: ProviderGateway) -> None:
    """Inject the gateway instance (called once at application startup)."""
    global _gateway  # noqa: PLW0603
    _gateway = gateway


@tool
def parse_document(object_key: str, file_type: str = "PDF") -> str:
    """Download a medical document from OSS and extract its text content.

    Use this tool when the user asks to read, analyse, or interpret a
    medical document (lab report, imaging report, prescription, etc.).

    Args:
        object_key: The OSS object key (path) of the file to parse.
        file_type: File type hint — "PDF" or "IMAGE".  Defaults to "PDF".

    Returns:
        Extracted text content from the document.
    """
    if _gateway is None:
        return "Error: document parsing service is not configured."

    try:
        result = _gateway.execute_with_resilience(
            "parse",
            {
                "assetRefs": [
                    {
                        "objectKey": object_key,
                        "fileType": file_type.upper(),
                    }
                ]
            },
        )
        if not result.success:
            return f"Error: failed to parse document — {result.error_code}"

        structured = (
            result.payload.get("structuredResult", {})
            if isinstance(result.payload.get("structuredResult", {}), dict)
            else {}
        )
        fields = structured.get("fields", [])
        if not isinstance(fields, list) or not fields:
            return "Warning: no text could be extracted from the document."

        lines: list[str] = []
        for field in fields:
            if not isinstance(field, dict):
                continue
            name = str(field.get("name", "")).strip()
            value = str(field.get("value", "")).strip()
            if not name or not value:
                continue
            unit = str(field.get("unit", "")).strip()
            reference_range = str(field.get("referenceRange", "")).strip()
            suffix_parts = [part for part in [unit, reference_range] if part]
            suffix = f" ({' / '.join(suffix_parts)})" if suffix_parts else ""
            lines.append(f"{name}: {value}{suffix}")

        if not lines:
            return "Warning: no text could be extracted from the document."
        return "\n".join(lines)

    except Exception as exc:
        LOGGER.warning("parse_document tool failed: %s", exc, exc_info=True)
        return f"Error: failed to parse document — {exc}"
