from __future__ import annotations

from app.agent.messages import AgentMessage, AgentToolCall
from app.agent.prompting import build_agent_instructions, build_untrusted_context, detect_recent_tool_error_names


def test_prompt_separates_fixed_instructions_from_untrusted_context() -> None:
    state = {
            "messages": [AgentMessage(role="user", content="帮我看这份报告")],
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
        }
    fixed = build_agent_instructions(state=state)
    untrusted = build_untrusted_context(state=state)

    assert "慢病随访" in fixed
    assert "复诊准备" in fixed
    assert "records/a.pdf" not in fixed
    assert "血糖控制欠佳" not in fixed
    assert untrusted is not None
    assert "非可信本轮数据" in untrusted
    assert "object_key=records/a.pdf" in untrusted
    assert "血糖控制欠佳" in untrusted


def test_detect_recent_tool_error_names_includes_only_current_turn() -> None:
    messages = [
        AgentMessage(role="user", content="第一轮"),
        AgentMessage(
            role="assistant",
            tool_calls=[AgentToolCall(id="old", name="parse_document", args={})],
        ),
        AgentMessage(role="tool", content="Error: 旧错误", name="parse_document", tool_call_id="old"),
        AgentMessage(role="user", content="第二轮"),
        AgentMessage(
            role="assistant",
            tool_calls=[AgentToolCall(id="new", name="generate_medical_text", args={})],
        ),
        AgentMessage(role="tool", content="Error: 新错误", name="generate_medical_text", tool_call_id="new"),
    ]

    assert detect_recent_tool_error_names(messages) == ["generate_medical_text"]
