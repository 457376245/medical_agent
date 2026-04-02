"""Tool: fetch disease profile context from backend-java."""

from __future__ import annotations

import json
import logging

from langchain_core.tools import tool

from app.services.disease_profile_context import DiseaseProfileContextClient

LOGGER = logging.getLogger(__name__)

_client: DiseaseProfileContextClient | None = None


def configure(client: DiseaseProfileContextClient) -> None:
    """Inject the Java context API client at startup."""
    global _client  # noqa: PLW0603
    _client = client


@tool
def fetch_disease_profile_context(
    disease_profile_id: str,
    record_id: str | None = None,
) -> str:
    """Fetch compact disease-profile context for the current conversation.

    Use this tool whenever you need current disease profile data from
    backend records, including selected report summary and trend snippets.

    Args:
        disease_profile_id: Required disease profile identifier.
        record_id: Optional focused report identifier under this profile.

    Returns:
        JSON text with context_status, profile summary, selected record
        summary, compact key fields, trend summary, and warnings.
    """
    profile_id = (disease_profile_id or "").strip()
    if not profile_id:
        return json.dumps(
            {
                "context_status": "unavailable",
                "warnings": ["disease_profile_id is required"],
            },
            ensure_ascii=False,
        )
    if _client is None:
        return json.dumps(
            {
                "context_status": "unavailable",
                "warnings": ["context client is not configured"],
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

