"""应用配置模块（非环境变量部分）。

环境敏感配置（API密钥、连接字符串）保存在 .env 文件中。
本模块管理应用行为参数、模型默认值和业务常量。
"""

from __future__ import annotations

import os

from app.utils import read_float_env, read_int_env, to_bool


# ---------------------------------------------------------------------------
# 模型默认配置
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
# Agent 行为配置
# ---------------------------------------------------------------------------
MAX_TOOL_ROUNDS: int = int(os.getenv("MAX_TOOL_ROUNDS", "10"))
"""单条用户消息允许的最大工具调用轮数。"""

SESSION_IDLE_TIMEOUT_SECONDS: int = int(
    os.getenv("SESSION_IDLE_TIMEOUT_SECONDS", "1800")
)
"""会话空闲超时时间，超过此时间无活动的会话可能被清理（30分钟）。"""

# ---------------------------------------------------------------------------
# 内存 / 持久化配置
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
# Java 上下文聚合 API（用于疾病档案上下文工具）
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
# LLM 网络/代理行为配置
# ---------------------------------------------------------------------------
LLM_PROXY_MODE: str = os.getenv("LLM_PROXY_MODE", "sanitize").strip().lower()

# ---------------------------------------------------------------------------
# LangSmith 可观测性配置
# ---------------------------------------------------------------------------
LANGCHAIN_TRACING_V2: bool = to_bool(os.getenv("LANGCHAIN_TRACING_V2", "false"))
LANGCHAIN_API_KEY: str = os.getenv("LANGCHAIN_API_KEY", "").strip()
LANGCHAIN_PROJECT: str = os.getenv("LANGCHAIN_PROJECT", "medical-agent").strip()
LANGCHAIN_ENDPOINT: str = os.getenv(
    "LANGCHAIN_ENDPOINT", "https://api.smith.langchain.com"
).strip()

# ---------------------------------------------------------------------------
# 业务常量（从现有代码迁移）
# ---------------------------------------------------------------------------
MAX_DOWNLOAD_BYTES: int = 20 * 1024 * 1024  # 20 MB
MAX_PDF_TEXT_CHARS: int = 18_000