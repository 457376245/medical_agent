"""工具注册中心。"""

from __future__ import annotations

import asyncio
import json
import time
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from agents import FunctionTool
from agents.tool_context import ToolContext

from app.tools.disease_profile_context import fetch_disease_profile_context
from app.tools.document_parse import parse_document
from app.tools.text_generate import generate_medical_text


@dataclass(frozen=True)
class ToolSpec:
    """模型可调用工具的定义。"""

    name: str
    description: str
    parameters: dict[str, Any]
    handler: Callable[..., str]


PRELOAD_TOOLS: list[ToolSpec] = [
    ToolSpec(
        name="fetch_disease_profile_context",
        description="获取当前对话的紧凑疾病档案上下文。",
        parameters={
            "type": "object",
            "properties": {
                "disease_profile_id": {"type": "string"},
                "record_id": {"type": "string"},
                "patient_id": {"type": "string"},
            },
            "required": ["disease_profile_id"],
        },
        handler=fetch_disease_profile_context,
    ),
]

MODEL_TOOLS: list[ToolSpec] = [
    ToolSpec(
        name="parse_document",
        description="从 OSS 下载医疗文档并提取其文本内容。",
        parameters={
            "type": "object",
            "properties": {
                "object_key": {"type": "string"},
                "file_type": {
                    "type": "string",
                    "enum": ["PDF", "IMAGE"],
                    "default": "PDF",
                },
            },
            "required": ["object_key"],
        },
        handler=parse_document,
    ),
    ToolSpec(
        name="generate_medical_text",
        description="生成医疗文本草稿（摘要、用药方案或报告分析）。",
        parameters={
            "type": "object",
            "properties": {
                "output_type": {
                    "type": "string",
                    "enum": ["SUMMARY", "MED_PLAN", "REPORT_ANALYSIS"],
                    "default": "SUMMARY",
                },
                "record_id": {"type": "string"},
                "context": {"type": "string", "default": "{}"},
            },
        },
        handler=generate_medical_text,
    ),
]

ALL_TOOLS: list[ToolSpec] = [
    *PRELOAD_TOOLS,
    *MODEL_TOOLS,
]


def get_tools() -> list[ToolSpec]:
    """返回所有可用工具。"""
    return list(ALL_TOOLS)


def get_preload_tools() -> list[ToolSpec]:
    """返回系统预加载工具，不直接暴露给模型选择。"""
    return list(PRELOAD_TOOLS)


def get_model_tools(metadata: dict | None = None) -> list[ToolSpec]:
    """返回模型可主动调用的工具。"""
    _ = metadata
    return list(MODEL_TOOLS)


def to_agents_tools(tools: list[ToolSpec]) -> list[FunctionTool]:
    """将项目工具定义转换为 OpenAI Agents SDK function tools。"""
    return [_to_agents_tool(tool) for tool in tools]


def _to_agents_tool(tool: ToolSpec) -> FunctionTool:
    async def invoke(context: ToolContext[Any], raw_args: str) -> str:
        args = _loads_tool_args(raw_args)
        run_context = getattr(context, "context", None)
        failed_keys = getattr(run_context, "failed_tool_keys", None)
        args_hash = _hash_tool_args(args)
        key = (tool.name, args_hash)
        if isinstance(failed_keys, set) and key in failed_keys:
            return "Error: 该工具使用相同参数已失败，请向用户说明情况并提供替代建议。"

        if tool.name == "parse_document":
            allowed = getattr(run_context, "allowed_attachment_keys", frozenset())
            object_key = str(args.get("object_key") or "").strip()
            if object_key not in allowed:
                _record_tool_diagnostic(run_context, tool.name, "denied", 0.0)
                return "Error: 未授权访问该附件。只能解析用户本轮明确提供的附件。"

        started = time.perf_counter()
        try:
            result = await asyncio.to_thread(tool.handler, **args)
        except TypeError as exc:
            result = f"Error: 工具参数错误 — {exc}"
        except Exception as exc:
            result = f"Error: 工具执行失败 — {exc}"

        content = str(result)
        status = "error" if content.strip().startswith("Error:") else "ok"
        _record_tool_diagnostic(
            run_context,
            tool.name,
            status,
            (time.perf_counter() - started) * 1000,
        )
        if content.strip().startswith("Error:") and isinstance(failed_keys, set):
            failed_keys.add(key)
        return content if len(content) <= 12_000 else content[:12_000] + "\n[工具输出已截断]"

    return FunctionTool(
        name=tool.name,
        description=tool.description,
        params_json_schema=tool.parameters,
        on_invoke_tool=invoke,
        strict_json_schema=False,
    )


def _loads_tool_args(value: str) -> dict[str, Any]:
    try:
        parsed = json.loads(value or "{}")
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def _hash_tool_args(value: dict[str, Any]) -> str:
    try:
        rendered = json.dumps(value or {}, ensure_ascii=False, sort_keys=True)
    except TypeError:
        rendered = str(value or {})
    import hashlib

    return hashlib.sha256(rendered.encode("utf-8")).hexdigest()[:12]


def _record_tool_diagnostic(run_context: Any, name: str, status: str, latency_ms: float) -> None:
    diagnostics = getattr(run_context, "diagnostics", None)
    if not isinstance(diagnostics, dict):
        return
    tools = diagnostics.setdefault("tools", [])
    if isinstance(tools, list):
        tools.append({"name": name, "status": status, "latency_ms": round(latency_ms, 3)})
