"""Tool: document parsing.

Wraps ``providers/storage`` + ``providers/document`` as an Agent-callable
tool.  When invoked, downloads a file from OSS and extracts its content.
"""

from __future__ import annotations

import logging

from langchain_core.tools import tool

from app.providers.document import DocumentParser
from app.providers.storage import OSSStorageService

LOGGER = logging.getLogger(__name__)

# Module-level singletons — replaced by DI from main.py at startup.
_storage: OSSStorageService | None = None
_document: DocumentParser | None = None


def configure(
    storage: OSSStorageService,
    document: DocumentParser,
) -> None:
    """Inject provider instances (called once at application startup)."""
    global _storage, _document  # noqa: PLW0603
    _storage = storage
    _document = document


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
    if _storage is None or _document is None:
        return "Error: document parsing service is not configured."

    try:
        content = _storage.download_bytes(object_key)
        parts = _document.build_parse_content(file_type.upper(), object_key, content)
        del content  # free raw bytes early

        # Extract text from the content parts list
        texts: list[str] = []
        for part in parts:
            if isinstance(part, dict) and part.get("type") == "text":
                texts.append(str(part.get("text", "")))
        result = "\n".join(texts).strip()
        if not result:
            return "Warning: no text could be extracted from the document."
        return result

    except Exception as exc:
        LOGGER.warning("parse_document tool failed: %s", exc, exc_info=True)
        return f"Error: failed to parse document — {exc}"
