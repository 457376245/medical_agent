"""工具：医疗文本生成。

将 providers/llm 封装为 Agent 可调用工具，用于生成医疗报告、摘要
和结构化输出。
"""

from __future__ import annotations

import json
import logging

from langchain_core.tools import tool

from app.providers.gateway import ProviderGateway

LOGGER = logging.getLogger(__name__)

# 模块级单例 —— 由 main.py 在启动时通过依赖注入替换。
_gateway: ProviderGateway | None = None


def configure(gateway: ProviderGateway) -> None:
    """注入 gateway 实例（应用启动时调用一次）。"""
    global _gateway  # noqa: PLW0603
    _gateway = gateway


@tool
def generate_medical_text(
    output_type: str = "SUMMARY",
    record_id: str = "",
    context: str = "{}",
) -> str:
    """生成医疗文本草稿（摘要、用药方案或报告分析）。

    当用户要求创建、起草或生成医疗文档（如临床摘要、用药方案、
    报告分析）时使用此工具。

    Args:
        output_type: "SUMMARY"、"MED_PLAN" 或 "REPORT_ANALYSIS" 之一。
        record_id: 医疗记录标识符。
        context: 额外分析上下文的 JSON 字符串。

    Returns:
        生成的医疗文本内容。
    """
    if _gateway is None:
        return "Error: 文本生成服务未配置。"

    try:
        analysis_context = json.loads(context) if context else {}
    except (json.JSONDecodeError, TypeError):
        analysis_context = {}

    payload = {
        "type": output_type,
        "recordId": record_id,
        "analysisContext": analysis_context,
    }

    try:
        result = _gateway.execute_with_resilience("generate", payload)
        if result.success:
            return str(result.payload.get("content", ""))
        return f"Error: 生成失败 — {result.error_code}"
    except Exception as exc:
        LOGGER.warning("generate_medical_text tool failed: %s", exc, exc_info=True)
        return f"Error: 生成失败 — {exc}"