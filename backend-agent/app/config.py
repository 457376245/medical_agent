"""Application configuration for non-environment settings.

Environment-sensitive config (API keys, connection strings) stays in .env.
This module holds application behavior parameters, model defaults, and
business constants.
"""

from __future__ import annotations

import os

from app.utils import read_float_env, read_int_env, to_bool


# ---------------------------------------------------------------------------
# Model defaults
# ---------------------------------------------------------------------------
OPENAI_BASE_URL: str = os.getenv("OPENAI_BASE_URL", "").strip()
OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "").strip()
OPENAI_AGENT_MODEL: str = (
    os.getenv("OPENAI_AGENT_MODEL", "").strip()
    or os.getenv("AGENT_MODEL", "").strip()
    or "gpt-5.4"
)
OPENAI_PARSE_MODEL: str = os.getenv("OPENAI_PARSE_MODEL", "gpt-5.4").strip()
OPENAI_GENERATE_MODEL: str = os.getenv(
    "OPENAI_GENERATE_MODEL", "gpt-5.4-mini"
).strip()
OPENAI_VISION_MODEL: str = os.getenv("OPENAI_VISION_MODEL", "gpt-5.4").strip()
OPENAI_FALLBACK_MODEL: str = os.getenv(
    "OPENAI_FALLBACK_MODEL", "gpt-5.4-mini"
).strip()
OPENAI_TEMPERATURE: float = read_float_env("OPENAI_TEMPERATURE", 0.0, 0.0)
OPENAI_REQUEST_TIMEOUT_SECONDS: float = read_float_env(
    "OPENAI_REQUEST_TIMEOUT_SECONDS", 90.0, 1.0
)
OPENAI_SDK_RETRIES: int = read_int_env("OPENAI_SDK_RETRIES", 2, 0)
OPENAI_TRUST_ENV: bool = to_bool(os.getenv("OPENAI_TRUST_ENV", "false"))
OPENAI_PROXY: str = os.getenv("OPENAI_PROXY", "").strip()
OPENAI_RETRY_WITH_ENV_PROXY: bool = to_bool(
    os.getenv("OPENAI_RETRY_WITH_ENV_PROXY", "true")
)

DEFAULT_AGENT_MODEL: str = OPENAI_AGENT_MODEL
DEFAULT_AGENT_TEMPERATURE: float = float(os.getenv("AGENT_TEMPERATURE", "0.3"))
DEFAULT_AGENT_MAX_TOKENS: int = int(os.getenv("AGENT_MAX_TOKENS", "4096"))

# ---------------------------------------------------------------------------
# Agent behavior
# ---------------------------------------------------------------------------
MAX_TOOL_ROUNDS: int = int(os.getenv("MAX_TOOL_ROUNDS", "10"))
"""Maximum number of tool-call rounds per single user message."""

SESSION_IDLE_TIMEOUT_SECONDS: int = int(
    os.getenv("SESSION_IDLE_TIMEOUT_SECONDS", "1800")
)
"""Sessions with no activity for this duration may be cleaned up (30 min)."""

# ---------------------------------------------------------------------------
# Memory / persistence
# ---------------------------------------------------------------------------
DATA_DIR: str = os.getenv("DATA_DIR", "data")
CHECKPOINT_DB_PATH: str = os.getenv("CHECKPOINT_DB_PATH", f"{DATA_DIR}/checkpoints.db")
MEMORY_DB_PATH: str = os.getenv("MEMORY_DB_PATH", f"{DATA_DIR}/memory.db")
CORS_ALLOW_ORIGINS: list[str] = [
    item.strip()
    for item in os.getenv(
        "CORS_ALLOW_ORIGINS",
        "http://localhost:3000,http://127.0.0.1:3000",
    ).split(",")
    if item.strip()
]

# ---------------------------------------------------------------------------
# Java context aggregation API (for disease profile context tool)
# ---------------------------------------------------------------------------
JAVA_API_BASE_URL: str = os.getenv("JAVA_API_BASE_URL", "http://localhost:8080")
JAVA_AGENT_CONTEXT_PATH: str = os.getenv(
    "JAVA_AGENT_CONTEXT_PATH",
    "/internal/agent",
)
JAVA_AGENT_CONTEXT_TIMEOUT_SECONDS: float = float(
    os.getenv("JAVA_AGENT_CONTEXT_TIMEOUT_SECONDS", "8")
)
JAVA_AGENT_API_KEY: str = os.getenv("JAVA_AGENT_API_KEY", "").strip()
JAVA_AGENT_API_KEY_HEADER: str = os.getenv(
    "JAVA_AGENT_API_KEY_HEADER",
    "X-Internal-Api-Key",
).strip()

# ---------------------------------------------------------------------------
# LLM network/proxy behavior
# ---------------------------------------------------------------------------
LLM_PROXY_MODE: str = os.getenv("LLM_PROXY_MODE", "sanitize").strip().lower()

# ---------------------------------------------------------------------------
# Business constants (migrated from existing code)
# ---------------------------------------------------------------------------
MAX_DOWNLOAD_BYTES: int = 20 * 1024 * 1024  # 20 MB
MAX_PDF_TEXT_CHARS: int = 18_000
