"""SSE 工具事件脱敏。"""

from __future__ import annotations

import json
from typing import Any


def sanitize_tool_input(tool_name: str, payload: Any) -> dict[str, Any]:
    """返回可暴露给前端的工具入参摘要。"""
    name = str(tool_name or "unknown")
    if name == "fetch_disease_profile_context":
        return {"status": "started", "summary": "正在加载疾病档案上下文"}
    if name == "parse_document":
        file_type = _pick_file_type(payload)
        return {
            "status": "started",
            "summary": "正在解析医疗文档",
            "file_type": file_type,
        }
    if name == "generate_medical_text":
        output_type = _pick(payload, "output_type") or _pick(payload, "type")
        return {
            "status": "started",
            "summary": "正在生成医疗文本草稿",
            "output_type": output_type or "SUMMARY",
        }
    return {"status": "started", "summary": "工具调用已开始"}


def sanitize_tool_output(tool_name: str, output: Any) -> dict[str, Any]:
    """返回可暴露给前端的工具输出摘要。"""
    name = str(tool_name or "unknown")
    text = _output_text(output)
    status = _status_from_text(text)

    if name == "fetch_disease_profile_context":
        context_status = _context_status(text)
        summary = {
            "ready": "疾病档案上下文已加载",
            "partial": "疾病档案上下文部分可用",
            "unavailable": "疾病档案上下文不可用",
        }.get(context_status, "疾病档案上下文处理完成")
        return {"status": context_status or status, "summary": summary}

    if name == "parse_document":
        field_count = _field_count(text)
        if status == "failed":
            summary = "医疗文档解析失败"
        elif field_count is not None:
            summary = f"医疗文档解析完成，提取到 {field_count} 个字段"
        else:
            summary = "医疗文档解析完成"
        return {"status": status, "summary": summary}

    if name == "generate_medical_text":
        summary = "医疗文本草稿生成失败" if status == "failed" else "医疗文本草稿生成完成"
        return {"status": status, "summary": summary}

    return {"status": status, "summary": "工具调用已完成"}


def _output_text(output: Any) -> str:
    content = getattr(output, "content", None)
    if content is not None:
        return str(content)
    if isinstance(output, list):
        return "\n".join(_output_text(item) for item in output)
    return str(output or "")


def _status_from_text(text: str) -> str:
    stripped = text.strip()
    if stripped.startswith("Error:"):
        return "failed"
    if stripped.startswith("Warning:"):
        return "warning"
    return "completed"


def _context_status(text: str) -> str | None:
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        return None
    if not isinstance(payload, dict):
        return None
    status = str(payload.get("context_status") or "").strip().lower()
    return status if status in {"ready", "partial", "unavailable"} else None


def _field_count(text: str) -> int | None:
    lines = [line for line in text.splitlines() if ":" in line]
    if lines:
        return len(lines)
    return None


def _pick(payload: Any, key: str) -> str | None:
    if not isinstance(payload, dict):
        return None
    value = payload.get(key)
    rendered = str(value).strip() if value is not None else ""
    return rendered or None


def _pick_file_type(payload: Any) -> str:
    value = _pick(payload, "file_type") or _pick(payload, "fileType")
    return (value or "PDF").upper()
