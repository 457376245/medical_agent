from __future__ import annotations

import logging
import os
from typing import Any


LOGGER = logging.getLogger(__name__)


def extract_error_codes(result: dict[str, Any]) -> list[str]:
    """从标准结果字典中提取错误码列表。"""
    return [
        str(item["code"])
        for item in result.get("errors", [])
        if isinstance(item, dict) and item.get("code")
    ]


def to_bool(value: str) -> bool:
    """将字符串解析为布尔值，无法识别时记录警告。"""
    normalized = value.strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off", ""}:
        return False
    LOGGER.warning("无法识别的布尔值 '%s'，默认为 False", value)
    return False


def read_float_env(key: str, default: float, minimum: float) -> float:
    """从环境变量读取浮点数，支持边界检查和默认值。"""
    raw = os.getenv(key, "").strip()
    if not raw:
        return default
    try:
        parsed = float(raw)
    except ValueError:
        LOGGER.warning("无效的浮点环境变量 %s=%s，使用默认值 %s", key, raw, default)
        return default
    return max(minimum, parsed)


def read_int_env(key: str, default: int, minimum: int) -> int:
    """从环境变量读取整数，支持边界检查和默认值。"""
    raw = os.getenv(key, "").strip()
    if not raw:
        return default
    try:
        parsed = int(raw)
    except ValueError:
        LOGGER.warning("无效的整数环境变量 %s=%s，使用默认值 %s", key, raw, default)
        return default
    return max(minimum, parsed)


def normalize_openai_base_url(value: str) -> str:
    """规范化 OpenAI 兼容的基础 URL，确保以 /v1 结尾。"""
    normalized = (value or "").strip().rstrip("/")
    if not normalized:
        return ""
    if normalized.endswith("/v1"):
        return normalized
    return f"{normalized}/v1"


def configure_llm_proxy_env(mode: str, no_proxy_hosts: list[str]) -> None:
    """规范化代理环境变量，可选绕过配置的 LLM 主机。

    支持的模式:
    - ``off``: 清除所有 HTTP(S)/ALL 代理环境变量
    - ``sanitize``: 保留代理设置，仅去除意外的引号
    - ``bypass_hosts``: 规范化代理并追加主机到 NO_PROXY
    """
    normalized_mode = (mode or "").strip().lower()
    proxy_keys = ["HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy"]

    # 去除引号以避免格式错误的代理 URL
    for key in proxy_keys:
        raw = os.getenv(key)
        if raw is None:
            continue
        cleaned = raw.strip().strip('"').strip("'")
        if cleaned != raw:
            os.environ[key] = cleaned
            LOGGER.info("代理环境变量已规范化: %s", key)

    if normalized_mode == "off":
        for key in proxy_keys:
            if key in os.environ:
                os.environ.pop(key, None)
        LOGGER.warning("LLM 代理模式=off; 所有 HTTP(S) 代理环境变量已清除")
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
        LOGGER.info("LLM 代理模式=bypass_hosts; NO_PROXY 包含: %s", ",".join(no_proxy_hosts))
        return

    LOGGER.warning("未知的 LLM_PROXY_MODE=%s; 使用默认 sanitize 模式", normalized_mode)
