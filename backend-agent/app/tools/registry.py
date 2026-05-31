"""工具注册中心。"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

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

    def to_openai_tool(self) -> dict[str, Any]:
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.parameters,
            },
        }


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
