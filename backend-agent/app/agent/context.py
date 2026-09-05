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
    patient_baseline = active_context_bundle.get("patient_baseline")
    current_medications = active_context_bundle.get("current_medications")
    care_goals = active_context_bundle.get("care_goals")
    personal_context = active_context_bundle.get("personal_context")
    follow_up_tasks = active_context_bundle.get("follow_up_tasks")
    red_flag_signals = active_context_bundle.get("red_flag_signals")
    evidence_refs = active_context_bundle.get("evidence_refs")
    evidence_ledger = active_context_bundle.get("evidence_ledger")
    pending_memories = active_context_bundle.get("pending_memories")
    warnings = active_context_bundle.get("warnings")

    lines: list[str] = []
    has_profile = False
    has_key_fields = False
    has_trends = False
    has_analysis_or_summary = False
    has_red_flags = False
    has_medications = False
    has_evidence_refs = False
    has_ultrasound_follow_up = False

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
            field_items = [item for item in key_fields if isinstance(item, Mapping)]
            field_items.sort(key=_key_field_priority)
            for item in field_items[:8]:
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

        ultrasound_follow_up = record_summary.get("ultrasound_follow_up")
        if isinstance(ultrasound_follow_up, Mapping):
            follow_summary = str(
                ultrasound_follow_up.get("patient_summary")
                or ultrasound_follow_up.get("summary")
                or ""
            ).strip()
            action_level = str(ultrasound_follow_up.get("action_level") or "").strip()
            action_suggestion = str(ultrasound_follow_up.get("action_suggestion") or "").strip()
            change_status = str(ultrasound_follow_up.get("change_status") or "").strip()
            confidence_level = str(ultrasound_follow_up.get("confidence_level") or "").strip()
            evidence_items = ultrasound_follow_up.get("current_evidence")
            finding_rows = ultrasound_follow_up.get("finding_rows")
            risk_modules = ultrasound_follow_up.get("risk_modules")
            missing_inputs = ultrasound_follow_up.get("missing_inputs")
            doctor_questions = ultrasound_follow_up.get("next_questions_for_doctor")
            rendered_evidence: list[str] = []
            if isinstance(evidence_items, list):
                for item in evidence_items[:3]:
                    if not isinstance(item, Mapping):
                        continue
                    label = str(item.get("label") or "").strip()
                    text = str(item.get("text") or "").strip()
                    if text:
                        rendered_evidence.append(f"{label}:{text}" if label else text)
            rendered_findings: list[str] = []
            if isinstance(finding_rows, list):
                for item in finding_rows[:6]:
                    if not isinstance(item, Mapping):
                        continue
                    module = str(item.get("module") or "").strip()
                    current_value = str(item.get("current_value") or "").strip()
                    trend_status = str(item.get("trend_status") or "").strip()
                    explanation = str(item.get("explanation") or "").strip()
                    if not module:
                        continue
                    segment = f"{module}:{current_value or '未提取'}"
                    if trend_status:
                        segment = f"{segment}（{trend_status}）"
                    if explanation:
                        segment = f"{segment}-{explanation}"
                    rendered_findings.append(segment)
            rendered_risks: list[str] = []
            if isinstance(risk_modules, list):
                for item in risk_modules[:3]:
                    if not isinstance(item, Mapping):
                        continue
                    name = str(item.get("name") or "").strip()
                    level = str(item.get("level") or "").strip()
                    risk_summary = str(item.get("summary") or "").strip()
                    if not name:
                        continue
                    segment = name
                    if level:
                        segment = f"{segment}({level})"
                    if risk_summary:
                        segment = f"{segment}:{risk_summary}"
                    rendered_risks.append(segment)
            rendered_missing: list[str] = []
            if isinstance(missing_inputs, list):
                for item in missing_inputs[:10]:
                    if not isinstance(item, Mapping):
                        continue
                    name = str(item.get("name") or "").strip()
                    if name:
                        rendered_missing.append(name)
            rendered_questions = [
                str(item).strip()
                for item in doctor_questions[:5]
                if isinstance(doctor_questions, list) and str(item).strip()
            ] if isinstance(doctor_questions, list) else []
            segments = [
                part
                for part in [follow_summary, change_status, action_level, action_suggestion, confidence_level]
                if part
            ]
            if segments:
                has_ultrasound_follow_up = True
                lines.append(f"- 超声/彩超随访：{'；'.join(segments)}")
            if rendered_findings:
                lines.append(f"- 超声/彩超结构化发现：{'；'.join(rendered_findings)}")
            if rendered_risks:
                lines.append(f"- 超声/彩超相关风险模块：{'；'.join(rendered_risks)}")
            if rendered_missing:
                lines.append(f"- 超声/彩超缺失信息：{'、'.join(rendered_missing)}")
            if rendered_questions:
                lines.append(f"- 超声/彩超复诊问题：{'；'.join(rendered_questions)}")
            if rendered_evidence:
                lines.append(f"- 超声/彩超原文依据：{'；'.join(rendered_evidence)}")

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

    if isinstance(patient_baseline, Mapping):
        diagnosed_conditions = [
            str(item).strip()
            for item in patient_baseline.get("diagnosed_conditions", [])
            if str(item).strip()
        ]
        allergies = [
            str(item).strip()
            for item in patient_baseline.get("allergies", [])
            if str(item).strip()
        ]
        abnormal_baseline = [
            str(item).strip()
            for item in patient_baseline.get("abnormal_baseline", [])
            if str(item).strip()
        ]
        doctor_instructions = str(patient_baseline.get("doctor_instructions") or "").strip()
        recent_symptoms = patient_baseline.get("recent_symptoms")
        if diagnosed_conditions:
            lines.append(f"- 已知慢病/诊断：{'；'.join(diagnosed_conditions[:4])}")
        if allergies:
            lines.append(f"- 过敏/禁忌：{'；'.join(allergies[:4])}")
        if abnormal_baseline:
            lines.append(f"- 既往异常基线：{'；'.join(abnormal_baseline[:4])}")
        if doctor_instructions:
            lines.append(f"- 医生交代事项：{doctor_instructions}")
        if isinstance(recent_symptoms, list) and recent_symptoms:
            rendered_symptoms: list[str] = []
            for item in recent_symptoms[:3]:
                if not isinstance(item, Mapping):
                    continue
                label = str(item.get("label") or "").strip()
                value = str(item.get("value") or "").strip()
                unit = str(item.get("unit") or "").strip()
                notes = str(item.get("notes") or "").strip()
                if not label:
                    continue
                segment = label
                if value:
                    segment = f"{segment}={value}{unit}"
                if notes:
                    segment = f"{segment}（{notes}）"
                rendered_symptoms.append(segment)
            if rendered_symptoms:
                lines.append(f"- 近期症状/体征：{'；'.join(rendered_symptoms)}")

    if isinstance(current_medications, list) and current_medications:
        rendered_meds: list[str] = []
        for item in current_medications[:5]:
            if not isinstance(item, Mapping):
                continue
            name = str(item.get("name") or "").strip()
            dosage = str(item.get("dosage") or "").strip()
            frequency = str(item.get("frequency") or "").strip()
            purpose = str(item.get("purpose") or "").strip()
            if not name:
                continue
            segment = name
            if dosage:
                segment = f"{segment} {dosage}"
            if frequency:
                segment = f"{segment} / {frequency}"
            if purpose:
                segment = f"{segment}（用途：{purpose}）"
            rendered_meds.append(segment)
        if rendered_meds:
            has_medications = True
            lines.append(f"- 当前用药：{'；'.join(rendered_meds)}")

    if isinstance(care_goals, list):
        rendered_goals = [str(item).strip() for item in care_goals[:4] if str(item).strip()]
        if rendered_goals:
            lines.append(f"- 当前健康目标：{'；'.join(rendered_goals)}")

    if isinstance(personal_context, list):
        rendered_context = [str(item).strip() for item in personal_context[:4] if str(item).strip()]
        if rendered_context:
            lines.append(f"- 患者个人背景/偏好：{'；'.join(rendered_context)}")

    if isinstance(follow_up_tasks, list):
        rendered_tasks: list[str] = []
        for item in follow_up_tasks[:4]:
            if not isinstance(item, Mapping):
                continue
            title = str(item.get("title") or "").strip()
            due_date = str(item.get("due_date") or "").strip()
            priority = str(item.get("priority") or "").strip()
            status_text = str(item.get("status") or "").strip()
            if not title:
                continue
            segment = title
            meta_parts = [part for part in [due_date, priority, status_text] if part]
            if meta_parts:
                segment = f"{segment}（{' / '.join(meta_parts)}）"
            rendered_tasks.append(segment)
        if rendered_tasks:
            lines.append(f"- 随访任务：{'；'.join(rendered_tasks)}")

    if isinstance(red_flag_signals, list):
        rendered_flags: list[str] = []
        for item in red_flag_signals[:3]:
            if not isinstance(item, Mapping):
                continue
            severity = str(item.get("severity") or "").strip().lower() or "watch"
            title = str(item.get("title") or "").strip()
            detail = str(item.get("detail") or "").strip()
            if not title:
                continue
            label = "高优先级"
            if severity == "warning":
                label = "需关注"
            elif severity == "watch":
                label = "观察"
            segment = f"{label}:{title}"
            if detail:
                segment = f"{segment}（{detail}）"
            rendered_flags.append(segment)
        if rendered_flags:
            has_red_flags = True
            lines.append(f"- 红旗信号：{'；'.join(rendered_flags)}")

    if isinstance(evidence_refs, list):
        rendered_evidence: list[str] = []
        for item in evidence_refs[:4]:
            if not isinstance(item, Mapping):
                continue
            title = str(item.get("title") or "").strip()
            type_name = str(item.get("type") or "").strip()
            confidence = str(item.get("confidence") or "").strip()
            nature = str(item.get("nature") or "").strip()
            source = str(item.get("source") or "").strip()
            if not title:
                continue
            segment = title
            meta_parts = [part for part in [type_name, confidence, nature, source] if part]
            if meta_parts:
                segment = f"{segment}（{' / '.join(meta_parts)}）"
            rendered_evidence.append(segment)
        if rendered_evidence:
            has_evidence_refs = True
            lines.append(f"- 证据来源：{'；'.join(rendered_evidence)}")

    if isinstance(evidence_ledger, list):
        rendered_ledger: list[str] = []
        for item in evidence_ledger[:24]:
            if not isinstance(item, Mapping):
                continue
            verification = str(item.get("verification_status") or "").strip().upper()
            if verification not in {"VERIFIED", "CONFIRMED", "CURRENT"}:
                continue
            evidence_id = str(item.get("evidence_id") or "").strip()
            summary = str(item.get("summary") or "").strip()
            source_type = str(item.get("source_type") or "").strip()
            source_ref = str(item.get("source_ref") or "").strip()
            observed_at = str(item.get("observed_at") or "").strip()
            updated_at = str(item.get("updated_at") or "").strip()
            if not evidence_id or not summary:
                continue
            meta = [value for value in (source_type, source_ref, observed_at or updated_at, verification) if value]
            rendered_ledger.append(f"[{evidence_id}] {summary}（{' / '.join(meta)}）")
        if rendered_ledger:
            has_evidence_refs = True
            lines.append("- 可引用证据：" + "；".join(rendered_ledger))

    if isinstance(pending_memories, list):
        rendered_memories: list[str] = []
        for item in pending_memories[:3]:
            if not isinstance(item, Mapping):
                continue
            field_path = str(item.get("field_path") or "").strip()
            value_text = str(item.get("value_text") or "").strip()
            risk_level = str(item.get("risk_level") or "").strip()
            if not field_path or not value_text:
                continue
            segment = f"{field_path}: {value_text}"
            if risk_level:
                segment = f"{segment}（{risk_level}）"
            rendered_memories.append(segment)
        if rendered_memories:
            lines.append(
                "- 待确认画像更新："
                + "；".join(rendered_memories)
                + "。这些内容尚未确认，不能当作已确认事实，只能提示用户确认。"
            )

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

    if has_ultrasound_follow_up:
        guidance.append(
            "[提示] 当前上下文包含超声/彩超结构化随访结果。回答相关问题时，"
            "先说明局部可判断趋势，再说明信息不足和需补充检查；"
            "不要把未提及当作阴性，不要声称做了图像分析或病灶级精准追踪。"
        )

    if has_medications:
        guidance.append(
            "[提示] 当前上下文已包含长期用药信息。涉及药物相关问题时，优先引用这些药物，"
            "并明确区分既有事实与药物风险推断。"
        )

    if has_red_flags:
        guidance.append(
            "[提示] 当前上下文存在红旗信号。请优先处理紧急程度，"
            "先说明是否需要尽快就医/复诊，再补充一般性解释。"
        )

    if has_evidence_refs:
        guidance.append(
            "[提示] 回答中请主动区分规则结论、趋势推断和长期画像记忆，"
            "关键医学事实必须引用上方稳定 evidence_id（例如 [E-xxxx]），"
            "并用“已知事实 / 可能解释 / 建议动作”的结构组织内容。"
        )

    result = prefix + "\n" + "\n".join(lines)
    if guidance:
        result += "\n\n" + "\n".join(guidance)
    return result


def _key_field_priority(item: Mapping[str, Any]) -> int:
    state = str(item.get("result_state") or item.get("resultState") or "").strip().lower()
    severity = str(item.get("severity") or item.get("alert_level") or item.get("alertLevel") or "").strip().lower()
    is_abnormal = item.get("is_abnormal")
    if state in {"critical", "panic"} or severity in {"critical", "high", "alert", "urgent"}:
        return 0
    if state in {"high", "low", "threshold", "abnormal"} or is_abnormal is True:
        return 1
    if severity in {"warning", "medium", "watch"}:
        return 2
    return 3
