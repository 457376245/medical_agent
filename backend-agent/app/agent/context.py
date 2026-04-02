"""Context signature and prompt helpers for disease-profile chat."""

from __future__ import annotations

import json
from typing import Any, Mapping


def context_signature_from_metadata(metadata: Mapping[str, Any] | None) -> str | None:
    """Build a stable context signature from disease profile + record."""
    if metadata is None:
        return None
    disease_profile_id = str(metadata.get("disease_profile_id") or "").strip()
    if not disease_profile_id:
        return None
    record_id = str(metadata.get("record_id") or "").strip()
    return f"{disease_profile_id}:{record_id}"


def should_refresh_context(
    *,
    metadata: Mapping[str, Any] | None,
    active_context_signature: str | None,
) -> bool:
    """Return True when context should be reloaded via the context tool."""
    next_signature = context_signature_from_metadata(metadata)
    if not next_signature:
        return False
    return next_signature != (active_context_signature or "")


def parse_context_bundle(content: str | None) -> dict[str, Any] | None:
    """Parse tool output JSON into a dictionary."""
    if not content:
        return None
    try:
        payload = json.loads(content)
    except json.JSONDecodeError:
        return None
    if not isinstance(payload, dict):
        return None
    return payload


def build_context_system_message(
    *,
    active_context_bundle: Mapping[str, Any] | None,
    active_context_status: str | None,
) -> str | None:
    """Convert context bundle into a compact system message."""
    status = str(active_context_status or "").strip().lower()
    if status == "unavailable":
        return (
            "当前疾病档案上下文加载失败或不可用。请明确告知用户当前结论受限，"
            "避免引用未成功加载的报告细节。"
        )

    if not isinstance(active_context_bundle, Mapping):
        return None

    disease_profile = active_context_bundle.get("disease_profile")
    selected_record = active_context_bundle.get("selected_record")
    record_summary = active_context_bundle.get("record_summary")
    trend_summary = active_context_bundle.get("trend_summary")
    warnings = active_context_bundle.get("warnings")

    lines: list[str] = []

    if isinstance(disease_profile, Mapping):
        disease_name = str(disease_profile.get("name") or "").strip()
        record_count = disease_profile.get("record_count")
        latest_record_at = str(disease_profile.get("latest_record_at") or "").strip()
        if disease_name:
            profile_line = f"- 疾病档案：{disease_name}"
            if isinstance(record_count, int):
                profile_line = f"{profile_line}（记录数：{record_count}）"
            if latest_record_at:
                profile_line = f"{profile_line}，最近记录：{latest_record_at}"
            lines.append(profile_line)

    if isinstance(selected_record, Mapping):
        record_title = str(selected_record.get("title") or "").strip()
        record_date = str(selected_record.get("record_date") or "").strip()
        source_type = str(selected_record.get("source_type") or "").strip()
        parse_status = str(selected_record.get("parse_status") or "").strip()
        selected_line = record_title or "已选择报告"
        if record_date:
            selected_line = f"{selected_line}（{record_date}）"
        if source_type:
            selected_line = f"{selected_line}，来源：{source_type}"
        if parse_status:
            selected_line = f"{selected_line}，解析状态：{parse_status}"
        lines.append(f"- 当前聚焦报告：{selected_line}")

    if isinstance(record_summary, Mapping):
        analysis = str(record_summary.get("analysis") or "").strip()
        summary = str(record_summary.get("summary") or "").strip()
        if analysis:
            lines.append(f"- 报告分析：{analysis}")
        elif summary:
            lines.append(f"- 报告摘要：{summary}")

        key_fields = record_summary.get("key_fields")
        if isinstance(key_fields, list) and key_fields:
            rendered_fields: list[str] = []
            for item in key_fields[:8]:
                if not isinstance(item, Mapping):
                    continue
                name = str(item.get("name") or "").strip()
                value = str(item.get("value") or "").strip()
                unit = str(item.get("unit") or "").strip()
                reference = str(item.get("reference_range") or "").strip()
                if not name or not value:
                    continue
                segment = f"{name}={value}{unit}"
                if reference:
                    segment = f"{segment}（参考范围：{reference}）"
                rendered_fields.append(segment)
            if rendered_fields:
                lines.append(f"- 关键字段：{'；'.join(rendered_fields)}")

    if isinstance(trend_summary, list) and trend_summary:
        rendered_trends: list[str] = []
        for item in trend_summary[:3]:
            if not isinstance(item, Mapping):
                continue
            label = str(item.get("record_date") or item.get("title") or "").strip()
            summary = str(item.get("summary") or "").strip()
            if not label or not summary:
                continue
            rendered_trends.append(f"{label}:{summary}")
        if rendered_trends:
            lines.append(f"- 趋势摘要：{'；'.join(rendered_trends)}")

    warning_list: list[str] = []
    if isinstance(warnings, list):
        for item in warnings:
            rendered = str(item).strip()
            if rendered:
                warning_list.append(rendered)
    if warning_list:
        lines.append(f"- 使用限制：{'；'.join(warning_list[:3])}")

    if not lines:
        return None

    prefix = "以下是当前会话已缓存的疾病档案上下文（由工具获取）："
    if status == "partial":
        prefix = (
            "以下是当前会话可用的部分疾病档案上下文（由工具获取）。"
            "若信息不足请明确说明限制："
        )
    return prefix + "\n" + "\n".join(lines)

