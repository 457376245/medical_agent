"""Application configuration for non-environment settings.

Environment-sensitive config (API keys, connection strings) stays in .env.
This module holds application behavior parameters, model defaults, and
business constants.
"""

from __future__ import annotations

import os


# ---------------------------------------------------------------------------
# Model defaults
# ---------------------------------------------------------------------------
DEFAULT_AGENT_MODEL: str = os.getenv("AGENT_MODEL", "gemini-2.5-flash")
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

# ---------------------------------------------------------------------------
# Business constants (migrated from existing code)
# ---------------------------------------------------------------------------
MAX_DOWNLOAD_BYTES: int = 20 * 1024 * 1024  # 20 MB
MAX_PDF_TEXT_CHARS: int = 18_000
