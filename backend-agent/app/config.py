"""应用配置模块。"""

from __future__ import annotations

import os

from app.utils import read_float_env, read_int_env, to_bool


# ---------------------------------------------------------------------------
# 模型默认配置（仅保留被其他模块 import 的部分）
# ---------------------------------------------------------------------------
OPENAI_BASE_URL: str = os.getenv("OPENAI_BASE_URL", "").strip()
OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "").strip()
OPENAI_AGENT_MODEL: str = (
    os.getenv("OPENAI_AGENT_MODEL", "").strip()
    or os.getenv("AGENT_MODEL", "").strip()
    or "gpt-5.4"
)
OPENAI_REQUEST_TIMEOUT_SECONDS: float = read_float_env(
    "OPENAI_REQUEST_TIMEOUT_SECONDS", 90.0, 1.0
)
OPENAI_SDK_RETRIES: int = read_int_env("OPENAI_SDK_RETRIES", 2, 0)

DEFAULT_AGENT_MODEL: str = OPENAI_AGENT_MODEL
DEFAULT_AGENT_TEMPERATURE: float = float(os.getenv("AGENT_TEMPERATURE", "0.3"))
DEFAULT_AGENT_MAX_TOKENS: int = int(os.getenv("AGENT_MAX_TOKENS", "4096"))

# ---------------------------------------------------------------------------
# Agent 行为配置
# ---------------------------------------------------------------------------
MAX_TOOL_ROUNDS: int = int(os.getenv("MAX_TOOL_ROUNDS", "10"))
AGENT_HISTORY_MAX_ITEMS: int = read_int_env("AGENT_HISTORY_MAX_ITEMS", 40, 2)
AGENT_HISTORY_MAX_TOKENS: int = read_int_env("AGENT_HISTORY_MAX_TOKENS", 12_000, 500)
AGENT_CONTEXT_MAX_CHARS: int = read_int_env("AGENT_CONTEXT_MAX_CHARS", 24_000, 1000)
AGENT_CONTEXT_CACHE_TTL_SECONDS: float = read_float_env(
    "AGENT_CONTEXT_CACHE_TTL_SECONDS", 0.0, 0.0
)
ANSWER_EVALUATOR_TIMEOUT_SECONDS: float = read_float_env(
    "ANSWER_EVALUATOR_TIMEOUT_SECONDS", 8.0, 0.1
)

# ---------------------------------------------------------------------------
# 内存 / 持久化配置
# ---------------------------------------------------------------------------
DATA_DIR: str = os.getenv("DATA_DIR", "data")
MEMORY_DB_PATH: str = os.getenv("MEMORY_DB_PATH", f"{DATA_DIR}/memory.db")
AGENT_SESSION_DB_PATH: str = os.getenv(
    "AGENT_SESSION_DB_PATH",
    f"{DATA_DIR}/agent_sessions.db",
)
CORS_ALLOW_ORIGINS: list[str] = [
    item.strip()
    for item in os.getenv(
        "CORS_ALLOW_ORIGINS",
        "http://127.0.0.1:3000",
    ).split(",")
    if item.strip()
]

# ---------------------------------------------------------------------------
# Java 上下文聚合 API
# ---------------------------------------------------------------------------
JAVA_API_BASE_URL: str = os.getenv("JAVA_API_BASE_URL", "http://127.0.0.1:8080")
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
# 患者长期画像记忆抽取
# ---------------------------------------------------------------------------
PATIENT_MEMORY_EXTRACTION_ENABLED: bool = to_bool(
    os.getenv("PATIENT_MEMORY_EXTRACTION_ENABLED", "true")
)
PATIENT_MEMORY_EXTRACTION_MODEL: str = os.getenv(
    "PATIENT_MEMORY_EXTRACTION_MODEL",
    OPENAI_AGENT_MODEL,
).strip()
PATIENT_MEMORY_EXTRACTION_TIMEOUT_SECONDS: float = read_float_env(
    "PATIENT_MEMORY_EXTRACTION_TIMEOUT_SECONDS",
    20.0,
    1.0,
)

# ---------------------------------------------------------------------------
# LLM 网络/代理行为配置
# ---------------------------------------------------------------------------
LLM_PROXY_MODE: str = os.getenv("LLM_PROXY_MODE", "sanitize").strip().lower()
