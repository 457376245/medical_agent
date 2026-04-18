"""场景特定的提示模板。

按业务场景组织，用作 system prompt 补充注入到 agent graph 中。
当 ChatRequest.metadata.scenario 匹配到已注册场景时自动生效。
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

_SCENARIO_REGISTRY: dict[str, str] = {
    "report_interpretation": REPORT_INTERPRETATION,
    "medication_review": MEDICATION_REVIEW,
    "clinical_summary": CLINICAL_SUMMARY,
}


def get_scenario_prompt(scenario: str | None) -> str | None:
    """根据 scenario 键返回对应的场景引导 prompt，未知 scenario 返回 None。"""
    if not scenario:
        return None
    return _SCENARIO_REGISTRY.get(scenario.strip().lower())
