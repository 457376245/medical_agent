from __future__ import annotations

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage

from app.agent.prompting import build_prompt_messages, detect_recent_tool_error_names


def test_prompt_composer_includes_context_scenario_and_attachments() -> None:
    result = build_prompt_messages(
        raw_messages=[HumanMessage(content="帮我看这份报告")],
        state={
            "messages": [HumanMessage(content="帮我看这份报告")],
            "metadata": {
                "workflow": "follow_up_prep",
                "scenario": "abnormal_reasoning",
                "audience": "patient",
                "urgency_level": "alert",
                "attachments": [
                    {
                        "object_key": "records/a.pdf",
                        "file_type": "PDF",
                        "display_name": "化验单",
                    }
                ],
            },
            "active_context_status": "ready",
            "active_context_bundle": {
                "disease_profile": {"name": "糖尿病", "record_count": 2},
                "record_summary": {
                    "analysis": "血糖控制欠佳",
                    "key_fields": [
                        {"name": "GLU", "value": "8.9", "unit": "mmol/L"},
                    ],
                },
            },
        },
        max_tokens=100_000,
    )

    system_content = str(result.messages[0].content)

    assert "慢病随访" in system_content
    assert "当前可用附件" in system_content
    assert "object_key=records/a.pdf" in system_content
    assert "疾病档案" in system_content
    assert "复诊准备" in system_content
    assert "指标异常根因推理" not in system_content
    assert "当前受众：患者" in system_content
    assert "当前紧急度：高" in system_content
    assert result.diagnostics["attachments_included"] is True
    assert result.diagnostics["workflow"] == "follow_up_prep"


def test_detect_recent_tool_error_names_includes_only_current_turn() -> None:
    messages = [
        HumanMessage(content="第一轮"),
        AIMessage(content="", tool_calls=[{"id": "old", "name": "parse_document", "args": {}}]),
        ToolMessage(content="Error: 旧错误", name="parse_document", tool_call_id="old"),
        HumanMessage(content="第二轮"),
        AIMessage(content="", tool_calls=[{"id": "new", "name": "generate_medical_text", "args": {}}]),
        ToolMessage(content="Error: 新错误", name="generate_medical_text", tool_call_id="new"),
    ]

    assert detect_recent_tool_error_names(messages) == ["generate_medical_text"]
