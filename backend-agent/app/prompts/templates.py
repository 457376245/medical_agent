"""场景与工作流特定的提示模板。

按业务场景和显式工作流组织，用作 system prompt 补充注入到 agent graph 中。
"""

from __future__ import annotations

REPORT_INTERPRETATION: str = (
    "【当前场景：医疗报告解读】\n\n"
    "用户正在查看一份医疗检验/检查报告，需要你协助解读。请遵循以下引导：\n\n"
    "1. 识别所有异常指标（resultState 为 high/low/threshold），用通俗语言解释其临床意义\n"
    "2. 如果上下文中包含指标联动分析（combinationAnalysis），优先引用这些已确认的组合模式，"
    "将规则摘要翻译为患者能理解的语言\n"
    "3. 给出合理的后续建议：复查时间、需要关注的指标、可能需要的进一步检查\n"
    "4. 使用适合普通患者理解的语言，首次出现专业术语时附带简短解释\n"
    "5. 对于严重异常建议及时就医，但避免制造恐慌\n"
    "6. 正常指标不需要逐项解释，可以一句话概括"
)

MEDICATION_REVIEW: str = (
    "【当前场景：用药方案审查】\n\n"
    "用户希望审查当前用药方案。请遵循以下引导：\n\n"
    "1. 检查药物之间是否存在已知的相互作用或配伍禁忌\n"
    "2. 结合检验指标评估药物可能的效果和副作用（如他汀与肝功能、降糖药与血糖）\n"
    "3. 评估当前剂量是否在常规范围内\n"
    "4. 提醒关键的用药注意事项（服药时间、饮食禁忌等）\n"
    "5. 明确告知：所有用药调整建议需经主治医师确认后方可执行\n"
    "6. 如果缺少关键信息（如具体药物名称、剂量），主动提问而非猜测"
)

CLINICAL_SUMMARY: str = (
    "【当前场景：临床摘要生成】\n\n"
    "用户需要生成一份检查或病历的临床摘要。请遵循以下引导：\n\n"
    "1. 提炼主要检查发现和诊断提示，不遗漏关键异常\n"
    "2. 如果有历史数据，标注关键指标的趋势变化（升高/降低/稳定）\n"
    "3. 给出当前状态的整体评估\n"
    "4. 列出后续随访建议\n"
    "5. 摘要应简洁专业，适合患者打印后带去就诊\n"
    "6. 使用清晰的分段结构，便于快速浏览"
)

ABNORMAL_REASONING: str = (
    "【当前场景：指标异常根因推理】\n\n"
    "用户希望深入分析异常指标的潜在原因。请遵循以下推理框架：\n\n"
    "1. 列出所有异常指标及其偏离方向（升高/降低/临界）\n"
    "2. 识别异常指标之间的关联模式"
    "（如肝功能组合异常：ALT+AST+GGT 同时升高提示肝细胞损伤）\n"
    "3. 基于关联模式提出可能的根因假设"
    "（如药物性肝损伤、病毒性肝炎、脂肪肝等）\n"
    "4. 对每个假设说明支持和不支持的证据\n"
    "5. 建议进一步检查以确认或排除假设"
    "（如加做肝炎标志物、腹部超声等）\n"
    "6. 明确告知：根因推理仅为辅助参考，确诊需由专业医师结合临床综合判断\n"
    "7. 如果异常指标孤立（无明显关联模式），说明可能的单一原因并建议复查确认"
)

FOLLOW_UP_PREP: str = (
    "【当前工作流：复诊准备】\n\n"
    "用户希望为下一次门诊/复查做准备。请遵循以下引导：\n\n"
    "1. 先给出本次随访最需要解决的 1-3 个核心问题\n"
    "2. 列出需要重点观察或复查的指标，并说明原因\n"
    "3. 输出复诊前准备清单，包括需要携带的报告、近期症状记录、正在使用的药物信息\n"
    "4. 如果上下文中已有随访任务、长期用药和健康目标，优先引用这些信息\n"
    "5. 明确哪些信号提示应提前就医，而不是等到原计划复诊\n"
    "6. 面向普通患者表达，强调下一步行动而不是抽象解释"
)

_SCENARIO_REGISTRY: dict[str, str] = {
    "report_interpretation": REPORT_INTERPRETATION,
    "medication_review": MEDICATION_REVIEW,
    "clinical_summary": CLINICAL_SUMMARY,
    "abnormal_reasoning": ABNORMAL_REASONING,
}

_WORKFLOW_REGISTRY: dict[str, str] = {
    "report_interpretation": REPORT_INTERPRETATION,
    "follow_up_prep": FOLLOW_UP_PREP,
    "medication_review": MEDICATION_REVIEW,
}


def get_scenario_prompt(scenario: str | None) -> str | None:
    """根据 scenario 键返回对应的场景引导 prompt，未知 scenario 返回 None。"""
    if not scenario:
        return None
    return _SCENARIO_REGISTRY.get(scenario.strip().lower())


def get_workflow_prompt(workflow: str | None) -> str | None:
    """根据 workflow 键返回对应的显式工作流 prompt。"""
    if not workflow:
        return None
    return _WORKFLOW_REGISTRY.get(workflow.strip().lower())


def get_conversation_prompt(
    *,
    workflow: str | None,
    scenario: str | None,
    audience: str | None,
    urgency_level: str | None,
) -> str | None:
    """组合工作流、受众和紧急程度提示。"""
    base_prompt = get_workflow_prompt(workflow) or get_scenario_prompt(scenario)
    audience_value = (audience or "").strip().lower()
    urgency_value = (urgency_level or "").strip().lower()

    extra_parts: list[str] = []

    if audience_value == "patient":
        extra_parts.append(
            "【当前受众：患者】\n"
            "请用患者容易理解的语言表达，首次出现医学术语时补充一句简短解释。"
        )
    elif audience_value == "caregiver":
        extra_parts.append(
            "【当前受众：照护者】\n"
            "请兼顾患者和家属的行动视角，突出需要协助观察、记录和提醒的事项。"
        )
    elif audience_value == "clinician":
        extra_parts.append(
            "【当前受众：临床人员】\n"
            "可适度提高专业度，但仍需明确事实、推断和建议的边界。"
        )

    if urgency_value in {"high", "alert", "urgent"}:
        extra_parts.append(
            "【当前紧急度：高】\n"
            "请优先进行就医分诊：先说明是否需要尽快就医/复诊，"
            "再补充原因与后续准备，不要把一般性解释放在最前面。"
        )
    elif urgency_value in {"watch", "warning", "medium"}:
        extra_parts.append(
            "【当前紧急度：需关注】\n"
            "请优先说明哪些变化值得加快复查，并给出清晰的时间优先级。"
        )

    parts = [part for part in [base_prompt, *extra_parts] if part]
    if not parts:
        return None
    return "\n\n".join(parts)
