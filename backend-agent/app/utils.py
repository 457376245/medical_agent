from __future__ import annotations

from typing import Any


def extract_error_codes(result: dict[str, Any]) -> list[str]:
    """Extract error codes from a standard result dict containing an 'errors' list."""
    return [
        str(item["code"])
        for item in result.get("errors", [])
        if isinstance(item, dict) and item.get("code")
    ]
