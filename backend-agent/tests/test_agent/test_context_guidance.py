from __future__ import annotations

from typing import Any

from app.agent.context import build_context_system_message


def _bundle(
    *,
    disease_profile: dict[str, Any] | None = None,
    selected_record: dict[str, Any] | None = None,
    record_summary: dict[str, Any] | None = None,
    trend_summary: list[dict[str, Any]] | None = None,
    warnings: list[str] | None = None,
) -> dict[str, Any]:
    return {
        "disease_profile": disease_profile or {"name": "高血压", "record_count": 3},
        "selected_record": selected_record,
        "record_summary": record_summary,
        "trend_summary": trend_summary or [],
        "warnings": warnings or [],
    }


def test_parse_status_pending_adds_guidance() -> None:
    bundle = _bundle(selected_record={"title": "血常规", "parse_status": "pending"})
    result = build_context_system_message(active_context_bundle=bundle, active_context_status="ready")
    assert result is not None
    assert "正在解析中" in result


def test_parse_status_failed_adds_guidance() -> None:
    bundle = _bundle(selected_record={"title": "血常规", "parse_status": "failed"})
    result = build_context_system_message(active_context_bundle=bundle, active_context_status="ready")
    assert result is not None
    assert "解析失败" in result


def test_key_fields_present_adds_reference_guidance() -> None:
    bundle = _bundle(
        selected_record={"title": "血常规", "parse_status": "completed"},
        record_summary={
            "analysis": "肝功能异常",
            "key_fields": [
                {"name": "ALT", "value": "85", "unit": "U/L", "reference_range": "0-40"},
            ],
        },
    )
    result = build_context_system_message(active_context_bundle=bundle, active_context_status="ready")
    assert result is not None
    assert "引用具体" in result


def test_trend_summary_present_adds_trend_guidance() -> None:
    bundle = _bundle(
        selected_record={"title": "血常规", "parse_status": "completed"},
        trend_summary=[
            {"record_date": "2026-03-01", "summary": "ALT 持续升高"},
        ],
    )
    result = build_context_system_message(active_context_bundle=bundle, active_context_status="ready")
    assert result is not None
    assert "变化方向" in result


def test_no_selected_record_adds_profile_guidance() -> None:
    bundle = _bundle(selected_record=None)
    result = build_context_system_message(active_context_bundle=bundle, active_context_status="ready")
    assert result is not None
    assert "未选择具体报告" in result


def test_partial_status_lists_missing_parts() -> None:
    bundle = _bundle(
        selected_record={"title": "血常规", "parse_status": "completed"},
        record_summary={"analysis": None, "summary": None, "key_fields": []},
    )
    result = build_context_system_message(active_context_bundle=bundle, active_context_status="partial")
    assert result is not None
    assert "报告分析" in result
    assert "关键指标数据" in result
    assert "历史趋势数据" in result
    assert "缺失信息" in result


def test_partial_status_no_specifics_when_all_present() -> None:
    bundle = _bundle(
        selected_record={"title": "血常规", "parse_status": "completed"},
        record_summary={
            "analysis": "正常",
            "key_fields": [{"name": "WBC", "value": "6.5", "unit": "10^9/L", "reference_range": "4-10"}],
        },
        trend_summary=[{"record_date": "2026-03-01", "summary": "稳定"}],
    )
    result = build_context_system_message(active_context_bundle=bundle, active_context_status="partial")
    assert result is not None
    assert "若信息不足请明确说明限制" in result
    assert "缺失信息" not in result


def test_guidance_appended_after_data_lines() -> None:
    bundle = _bundle(
        selected_record=None,
        trend_summary=[{"record_date": "2026-03-01", "summary": "ALT 升高"}],
    )
    result = build_context_system_message(active_context_bundle=bundle, active_context_status="ready")
    assert result is not None
    data_end = result.index("趋势摘要")
    guidance_start = result.index("[提示]")
    assert guidance_start > data_end
