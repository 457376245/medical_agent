from __future__ import annotations

from app.prompts.templates import get_conversation_prompt, get_scenario_prompt, get_workflow_prompt


def test_abnormal_reasoning_template_registered() -> None:
    prompt = get_scenario_prompt("abnormal_reasoning")
    assert prompt is not None


def test_abnormal_reasoning_contains_reasoning_framework() -> None:
    prompt = get_scenario_prompt("abnormal_reasoning")
    assert prompt is not None
    assert "关联模式" in prompt
    assert "根因假设" in prompt
    assert "进一步检查" in prompt
    assert "辅助参考" in prompt


def test_all_scenarios_registered() -> None:
    for key in ("report_interpretation", "medication_review", "clinical_summary", "abnormal_reasoning"):
        assert get_scenario_prompt(key) is not None, f"scenario {key} not registered"


def test_unknown_scenario_returns_none() -> None:
    assert get_scenario_prompt("nonexistent") is None
    assert get_scenario_prompt(None) is None
    assert get_scenario_prompt("") is None


def test_follow_up_workflow_prompt_registered() -> None:
    prompt = get_workflow_prompt("follow_up_prep")
    assert prompt is not None
    assert "复诊准备" in prompt
    assert "复诊前准备清单" in prompt


def test_conversation_prompt_includes_audience_and_urgency_hints() -> None:
    prompt = get_conversation_prompt(
        workflow="follow_up_prep",
        scenario=None,
        audience="patient",
        urgency_level="alert",
    )
    assert prompt is not None
    assert "当前受众：患者" in prompt
    assert "当前紧急度：高" in prompt
