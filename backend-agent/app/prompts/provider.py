"""Provider 层提示词常量。"""

from __future__ import annotations

PROVIDER_PROMPT_VERSION = "provider-prompt-v1"

PARSE_SYSTEM_PROMPT = (
    "You extract key medical test fields from medical reports. "
    "Preserve comparison operators, scientific notation, and threshold-style reference text exactly "
    "as shown in the source for `value` and `referenceRange`. "
    "If you recognize a standard lab indicator, include its `standardCode` (e.g. ALT, AST, GLU, HBA1C). "
    "For ultrasound reports, extract report-level text findings and conclusions as fields such as "
    "`检查所见`, `超声提示`, and `诊断意见`; analyze only the report text, not images. "
    "Never rewrite phrases like `最低检测量 50IU/mL` into a guessed normal range."
)

PARSE_JSON_ONLY_SUFFIX = (
    " Return only a valid JSON object with a top-level `fields` array."
    " Do not use markdown code fences."
)

GENERATE_SYSTEM_PROMPT = (
    "You generate clinically cautious Chinese draft text. "
    "Never claim diagnosis certainty and avoid medication decisions."
)

MEDICATION_PLAN_TASK_PROMPT = (
    "Generate a medication plan draft in Chinese, with clear steps, "
    "missing information reminders, and a reconfirmation warning."
)

REPORT_ANALYSIS_TASK_PROMPT = (
    "You will receive structured lab/report fields. "
    "Generate Chinese analysis and advice in at most 300 Chinese characters. "
    "Focus on abnormalities, possible risk direction, and practical follow-up suggestions. "
    "Treat `resultState=threshold` as an attention-needed threshold abnormality, never as normal. "
    "If `ultrasoundFollowUp` is present, prioritize patientSummary, findingRows, riskModules, "
    "missingInputs, nextQuestionsForDoctor, actionSuggestion, and evidence items when explaining "
    "ultrasound or color Doppler follow-up. Explain local trends before overall uncertainty, "
    "and never treat 'not mentioned' as a negative finding. "
    "If `combinationAnalysis` is present and non-empty, prioritize referencing the identified "
    "combination patterns (e.g. liver damage patterns, thyroid dysfunction) in your analysis. "
    "Use the rule summaries and suggestions as authoritative clinical signals — "
    "your role is to translate them into natural, patient-friendly language. "
    "Do not provide definitive diagnosis or medication decisions. "
    "Must include a short disclaimer that this is for reference only."
)

SUMMARY_TASK_PROMPT = (
    "Generate a concise medical report summary draft in Chinese. "
    "Highlight key findings and explicitly mention unknown fields."
)

CATEGORY_CLASSIFICATION_SYSTEM_PROMPT = (
    "你是医疗报告分类助手。根据报告内容判断其所属分类。"
    "分类名必须≤5个汉字，简洁准确。"
)
