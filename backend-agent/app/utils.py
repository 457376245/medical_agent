from __future__ import annotations

import logging
import os
from typing import Any


LOGGER = logging.getLogger(__name__)


def extract_error_codes(result: dict[str, Any]) -> list[str]:
    """Extract error codes from a standard result dict containing an 'errors' list."""
    return [
        str(item["code"])
        for item in result.get("errors", [])
        if isinstance(item, dict) and item.get("code")
    ]


def to_bool(value: str) -> bool:
    """Parse a string into a boolean with logging for unrecognized values."""
    normalized = value.strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off", ""}:
        return False
    LOGGER.warning("Unrecognized boolean value '%s', treating as False", value)
    return False


def read_float_env(key: str, default: float, minimum: float) -> float:
    """Read a float from an environment variable with bounds and fallback."""
    raw = os.getenv(key, "").strip()
    if not raw:
        return default
    try:
        parsed = float(raw)
    except ValueError:
        LOGGER.warning("Invalid float env %s=%s, fallback to %s", key, raw, default)
        return default
    return max(minimum, parsed)


def read_int_env(key: str, default: int, minimum: int) -> int:
    """Read an int from an environment variable with bounds and fallback."""
    raw = os.getenv(key, "").strip()
    if not raw:
        return default
    try:
        parsed = int(raw)
    except ValueError:
        LOGGER.warning("Invalid int env %s=%s, fallback to %s", key, raw, default)
        return default
    return max(minimum, parsed)
