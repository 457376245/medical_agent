"""工具：文档解析。

将 provider gateway 封装为 Agent 可调用工具，并将结构化解析结果
格式化为对话层的纯文本。
"""

from __future__ import annotations

import logging

from app.providers.gateway import ProviderGateway

LOGGER = logging.getLogger(__name__)

_gateway: ProviderGateway | None = None


def configure(gateway: ProviderGateway) -> None:
    """注入 gateway 实例（应用启动时调用一次）。"""
    global _gateway  # noqa: PLW0603
    _gateway = gateway


def parse_document(object_key: str, file_type: str = "PDF") -> str:
    """从 OSS 下载医疗文档并提取其文本内容。

    当用户要求读取、分析或解读医疗文档（化验报告、影像报告、处方等）
    时使用此工具。

    Args:
        object_key: 要解析文件的 OSS 对象键（路径）。
        file_type: 文件类型提示 —— "PDF" 或 "IMAGE"。默认为 "PDF"。

    Returns:
        从文档提取的文本内容。
    """
    if _gateway is None:
        return "Error: 文档解析服务未配置。"

    try:
        result = _gateway.execute_with_resilience(
            "parse",
            {
                "assetRefs": [
                    {
                        "objectKey": object_key,
                        "fileType": file_type.upper(),
                    }
                ]
            },
        )
        if not result.success:
            return f"Error: 文档解析失败 — {result.error_code}"

        structured = (
            result.payload.get("structuredResult", {})
            if isinstance(result.payload.get("structuredResult", {}), dict)
            else {}
        )
        fields = structured.get("fields", [])
        if not isinstance(fields, list) or not fields:
            return "Warning: 无法从文档中提取文本。"

        lines: list[str] = []
        for field in fields:
            if not isinstance(field, dict):
                continue
            name = str(field.get("name", "")).strip()
            value = str(field.get("value", "")).strip()
            if not name or not value:
                continue
            unit = str(field.get("unit", "")).strip()
            reference_range = str(field.get("referenceRange", "")).strip()
            suffix_parts = [part for part in [unit, reference_range] if part]
            suffix = f" ({' / '.join(suffix_parts)})" if suffix_parts else ""
            lines.append(f"{name}: {value}{suffix}")

        if not lines:
            return "Warning: 无法从文档中提取文本。"
        return "\n".join(lines)

    except Exception as exc:
        LOGGER.warning("parse_document tool failed: %s", exc, exc_info=True)
        return f"Error: 文档解析失败 — {exc}"
