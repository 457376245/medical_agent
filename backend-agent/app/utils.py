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


def normalize_openai_base_url(value: str) -> str:
    """Normalize OpenAI-compatible base URL to the API root ending with /v1."""
    normalized = (value or "").strip().rstrip("/")
    if not normalized:
        return ""
    if normalized.endswith("/v1"):
        return normalized
    return f"{normalized}/v1"


def configure_llm_proxy_env(mode: str, no_proxy_hosts: list[str]) -> None:
    """Normalize proxy env vars and optionally bypass proxy for configured LLM hosts.

    Supported modes:
    - ``off``: clear HTTP(S)/ALL proxy envs.
    - ``sanitize``: keep proxies, only strip accidental surrounding quotes.
    - ``bypass_hosts``: sanitize proxies and append hosts to NO_PROXY.
    """
    normalized_mode = (mode or "").strip().lower()
    proxy_keys = ["HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy"]

    # Strip wrapping quotes to avoid malformed proxy URLs such as "http://127.0.0.1:7897"
    for key in proxy_keys:
        raw = os.getenv(key)
        if raw is None:
            continue
        cleaned = raw.strip().strip('"').strip("'")
        if cleaned != raw:
            os.environ[key] = cleaned
            LOGGER.info("Proxy env sanitized: %s", key)

    if normalized_mode == "off":
        for key in proxy_keys:
            if key in os.environ:
                os.environ.pop(key, None)
        LOGGER.warning("LLM proxy mode=off; all HTTP(S) proxy envs are cleared")
        return

    if normalized_mode == "sanitize":
        return

    if normalized_mode == "bypass_hosts":
        existing = os.getenv("NO_PROXY") or os.getenv("no_proxy") or ""
        current_items = [item.strip() for item in existing.split(",") if item.strip()]
        for host in no_proxy_hosts:
            if host not in current_items:
                current_items.append(host)
        merged = ",".join(current_items)
        os.environ["NO_PROXY"] = merged
        os.environ["no_proxy"] = merged
        LOGGER.info("LLM proxy mode=bypass_hosts; NO_PROXY includes: %s", ",".join(no_proxy_hosts))
        return

    LOGGER.warning("Unknown LLM_PROXY_MODE=%s; fallback to sanitize", normalized_mode)
