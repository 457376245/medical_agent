from __future__ import annotations

from app.api.tool_events import sanitize_tool_input, sanitize_tool_output


def test_context_tool_events_do_not_expose_ids_or_raw_json() -> None:
    public_input = sanitize_tool_input(
        "fetch_disease_profile_context",
        {"disease_profile_id": "profile-1", "patient_id": "patient-1"},
    )
    public_output = sanitize_tool_output(
        "fetch_disease_profile_context",
        '{"context_status":"ready","disease_profile":{"id":"profile-1"}}',
    )

    assert "profile-1" not in str(public_input)
    assert "patient-1" not in str(public_input)
    assert "profile-1" not in str(public_output)
    assert public_output["summary"] == "疾病档案上下文已加载"


def test_parse_tool_events_hide_object_key_and_full_fields() -> None:
    public_input = sanitize_tool_input(
        "parse_document",
        {"object_key": "private/report.pdf", "file_type": "PDF"},
    )
    public_output = sanitize_tool_output(
        "parse_document",
        "ALT: 85 (0-40)\nAST: 90 (0-40)",
    )

    assert "private/report.pdf" not in str(public_input)
    assert "ALT" not in str(public_output)
    assert public_output["summary"] == "医疗文档解析完成，提取到 2 个字段"


def test_generate_tool_events_hide_generated_text() -> None:
    public_output = sanitize_tool_output(
        "generate_medical_text",
        "这是一段较长的医疗文本草稿",
    )

    assert "医疗文本草稿生成完成" == public_output["summary"]
    assert "这是一段" not in str(public_output)
