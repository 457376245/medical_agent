"""疾病档案聊天的上下文签名和提示辅助函数。"""

from __future__ import annotations

import json
from typing import Any, Mapping


def context_signature_from_metadata(metadata: Mapping[str, Any] | None) -> str | None:
    """从疾病档案和记录构建稳定的上下文签名。"""
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
    """返回是否需要通过上下文工具重新加载上下文。"""
    next_signature = context_signature_from_metadata(metadata)
    if not next_signature:
        return False
    return next_signature != (active_context_signature or "")


def parse_context_bundle(content: str | None) -> dict[str, Any] | None:
    """将工具输出 JSON 解析为字典。"""
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
    """将上下文数据包转换为紧凑的系统消息，并根据数据状态附加动态引导。"""
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
    has_profile = False
    has_key_fields = False
    has_trends = False
    has_analysis_or_summary = False

    if isinstance(disease_profile, Mapping):
        disease_name = str(disease_profile.get("name") or "").strip()
        record_count = disease_profile.get("record_count")
        latest_record_at = str(disease_profile.get("latest_record_at") or "").strip()
        if disease_name:
            has_profile = True
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
        summary_text = str(record_summary.get("summary") or "").strip()
        has_analysis_or_summary = bool(analysis or summary_text)
        if analysis:
            lines.append(f"- 报告分析：{analysis}")
        elif summary_text:
            lines.append(f"- 报告摘要：{summary_text}")

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
                has_key_fields = True
                lines.append(f"- 关键字段：{'；'.join(rendered_fields)}")

    if isinstance(trend_summary, list) and trend_summary:
        rendered_trends: list[str] = []
        for item in trend_summary[:3]:
            if not isinstance(item, Mapping):
                continue
            label = str(item.get("record_date") or item.get("title") or "").strip()
            trend_text = str(item.get("summary") or "").strip()
            if not label or not trend_text:
                continue
            rendered_trends.append(f"{label}:{trend_text}")
        if rendered_trends:
            has_trends = True
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

    # --- 前缀 ---
    prefix = "以下是当前会话已缓存的疾病档案上下文（由工具获取）："
    if status == "partial":
        missing: list[str] = []
        if not has_analysis_or_summary:
            missing.append("报告分析")
        if not has_key_fields:
            missing.append("关键指标数据")
        if not has_trends:
            missing.append("历史趋势数据")
        if missing:
            prefix = (
                "以下是当前会话可用的部分疾病档案上下文（由工具获取）。"
                f"缺失信息：{'、'.join(missing)}。"
                "请在回答中明确说明这些数据局限性："
            )
        else:
            prefix = (
                "以下是当前会话可用的部分疾病档案上下文（由工具获取）。"
                "若信息不足请明确说明限制："
            )

    # --- 动态引导 ---
    guidance: list[str] = []

    if isinstance(selected_record, Mapping):
        ps = str(selected_record.get("parse_status") or "").strip().lower()
        if ps == "pending":
            guidance.append(
                "[提示] 当前报告正在解析中，结构化数据可能不完整。"
                "请基于已有信息回答，并告知用户完整分析需等待解析完成。"
            )
        elif ps == "failed":
            guidance.append(
                "[提示] 当前报告解析失败，无法获取结构化指标数据。"
                "请基于报告标题和基本信息提供有限分析，并建议用户重新上传或联系支持。"
            )
    elif has_profile:
        guidance.append(
            "[提示] 当前未选择具体报告，请基于疾病档案整体信息提供概况性建议。"
            "如需分析具体报告，可提示用户选择一份报告。"
        )

    if has_key_fields:
        guidance.append(
            "[提示] 上方列出了关键检验指标及参考范围，"
            "回答时请引用具体异常数值，分析其临床意义并与参考范围对比说明。"
        )

    if has_trends:
        guidance.append(
            "[提示] 上方包含历史趋势数据，回答时请分析指标随时间的变化方向"
            "（升高/降低/稳定），并结合趋势讨论当前值的临床意义。"
        )

    result = prefix + "\n" + "\n".join(lines)
    if guidance:
        result += "\n\n" + "\n".join(guidance)
    return result