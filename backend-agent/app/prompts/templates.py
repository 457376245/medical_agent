"""场景特定的提示模板。

按业务场景组织（咨询、报告解读、用药审查等）。
模板支持通过 str.format() 或 f-string 进行变量插值。
"""

from __future__ import annotations

REPORT_INTERPRETATION: str = (
    "请分析以下医疗报告内容，提取关键发现并给出解读：\n\n"
    "{report_content}\n\n"
    "请从以下角度进行分析：\n"
    "1. 关键异常指标\n"
    "2. 可能的临床意义\n"
    "3. 建议的后续检查或处理"
)

MEDICATION_REVIEW: str = (
    "请审查以下用药方案：\n\n"
    "患者信息：{patient_info}\n"
    "当前用药：{medications}\n\n"
    "请关注：\n"
    "1. 药物相互作用\n"
    "2. 剂量合理性\n"
    "3. 禁忌症检查\n"
    "4. 用药注意事项"
)

CLINICAL_SUMMARY: str = (
    "请根据以下病历信息生成临床摘要：\n\n"
    "{medical_records}\n\n"
    "摘要应包含：\n"
    "1. 主诉与现病史\n"
    "2. 主要诊断\n"
    "3. 治疗经过\n"
    "4. 当前状态与转归"
)