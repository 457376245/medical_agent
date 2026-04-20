from __future__ import annotations

from app.prompts.system import SYSTEM_MEDICAL_ASSISTANT


def test_core_principles_present() -> None:
    assert "慢病随访" in SYSTEM_MEDICAL_ASSISTANT
    assert "循证医学" in SYSTEM_MEDICAL_ASSISTANT
    assert "事实陈述和推理建议" in SYSTEM_MEDICAL_ASSISTANT
    assert "不编造信息" in SYSTEM_MEDICAL_ASSISTANT
    assert "专业医师确认" in SYSTEM_MEDICAL_ASSISTANT
    assert "患者隐私" in SYSTEM_MEDICAL_ASSISTANT


def test_tool_strategy_present() -> None:
    assert "fetch_disease_profile_context" in SYSTEM_MEDICAL_ASSISTANT
    assert "parse_document" in SYSTEM_MEDICAL_ASSISTANT
    assert "generate_medical_text" in SYSTEM_MEDICAL_ASSISTANT
    assert "\u4e0d\u8c03\u7528\u4efb\u4f55\u5de5\u5177" in SYSTEM_MEDICAL_ASSISTANT


def test_response_structure_present() -> None:
    assert "已知事实" in SYSTEM_MEDICAL_ASSISTANT
    assert "可能解释" in SYSTEM_MEDICAL_ASSISTANT
    assert "建议动作" in SYSTEM_MEDICAL_ASSISTANT
    assert "结构化行动闭环" in SYSTEM_MEDICAL_ASSISTANT


def test_chronic_care_context_guidance_present() -> None:
    assert "当前用药" in SYSTEM_MEDICAL_ASSISTANT
    assert "红旗信号" in SYSTEM_MEDICAL_ASSISTANT
    assert "证据来源" in SYSTEM_MEDICAL_ASSISTANT


def test_error_handling_guidance_present() -> None:
    assert "Error:" in SYSTEM_MEDICAL_ASSISTANT
    assert "相同参数" in SYSTEM_MEDICAL_ASSISTANT
    assert "用户友好" in SYSTEM_MEDICAL_ASSISTANT
