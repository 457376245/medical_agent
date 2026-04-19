from __future__ import annotations

from app.prompts.system import SYSTEM_MEDICAL_ASSISTANT


def test_core_principles_present() -> None:
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
    assert "发现" in SYSTEM_MEDICAL_ASSISTANT
    assert "解读" in SYSTEM_MEDICAL_ASSISTANT
    assert "建议" in SYSTEM_MEDICAL_ASSISTANT
    assert "三段式结构" in SYSTEM_MEDICAL_ASSISTANT
