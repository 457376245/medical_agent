"""工具：从 backend-java 获取疾病档案上下文。"""

from __future__ import annotations

import json
import logging

from langchain_core.tools import tool

from app.services.disease_profile_context import DiseaseProfileContextClient

LOGGER = logging.getLogger(__name__)

_client: DiseaseProfileContextClient | None = None


def configure(client: DiseaseProfileContextClient) -> None:
    """在启动时注入 Java 上下文 API 客户端。"""
    global _client  # noqa: PLW0603
    _client = client


@tool
def fetch_disease_profile_context(
    disease_profile_id: str,
    record_id: str | None = None,
    patient_id: str | None = None,
) -> str:
    """获取当前对话的紧凑疾病档案上下文。

    当你需要来自后端记录的当前疾病档案数据时使用此工具，
    包括选中的报告摘要和趋势片段。

    Args:
        disease_profile_id: 必需的疾病档案标识符。
        record_id: 可选的该档案下聚焦报告标识符。
        patient_id: 可选的患者标识符，用于访问范围限定。

    Returns:
        JSON 文本，包含 context_status、档案摘要、选中记录摘要、
        紧凑关键字段、趋势摘要和警告。
    """
    profile_id = (disease_profile_id or "").strip()
    if not profile_id:
        return json.dumps(
            {
                "context_status": "unavailable",
                "warnings": ["disease_profile_id 是必需的"],
            },
            ensure_ascii=False,
        )
    if _client is None:
        return json.dumps(
            {
                "context_status": "unavailable",
                "warnings": ["上下文客户端未配置"],
            },
            ensure_ascii=False,
        )

    LOGGER.info(
        "context_fetch_started profile_id=%s record_id=%s",
        profile_id,
        (record_id or "").strip() or None,
    )

    bundle = _client.fetch_context_bundle(
        disease_profile_id=profile_id,
        record_id=(record_id or "").strip() or None,
        patient_id=(patient_id or "").strip() or None,
    )
    status = str(bundle.get("context_status", "unavailable")).lower()
    if status == "ready":
        LOGGER.info(
            "context_fetch_succeeded profile_id=%s record_id=%s",
            profile_id,
            (record_id or "").strip() or None,
        )
    elif status == "partial":
        LOGGER.info(
            "context_fetch_partial profile_id=%s record_id=%s warnings=%s",
            profile_id,
            (record_id or "").strip() or None,
            bundle.get("warnings", []),
        )
    else:
        LOGGER.warning(
            "context_fetch_failed profile_id=%s record_id=%s warnings=%s",
            profile_id,
            (record_id or "").strip() or None,
            bundle.get("warnings", []),
        )

    return json.dumps(bundle, ensure_ascii=False)