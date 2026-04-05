"""HTTP client for Java aggregated disease profile context endpoint."""
from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Callable, Literal

ContextStatus = Literal["ready", "partial", "unavailable"]


@dataclass(slots=True)
class _HttpFailure(Exception): status_code: int; code: str | None; message: str


def _txt(value: Any) -> str: return str(value).strip() if value is not None else ""


def _opt(value: Any) -> str | None: return _txt(value) or None


def _dict(value: Any) -> dict[str, Any]: return value if isinstance(value, dict) else {}


def _dict_list(value: Any) -> list[dict[str, Any]]:
    return [item for item in value] if isinstance(value, list) and all(isinstance(item, dict) for item in value) else []


def _status(value: Any) -> ContextStatus:
    normalized = _txt(value).lower()
    return normalized if normalized in {"ready", "partial"} else "unavailable"


def _http_get_json(url: str, *, headers: dict[str, str], timeout_seconds: float) -> dict[str, Any]:
    request = urllib.request.Request(url=url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            payload = json.loads(response.read().decode("utf-8") or "{}")
            return payload if isinstance(payload, dict) else {}
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="ignore")
        try:
            err = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            err = {}
        raise _HttpFailure(
            int(error.code),
            _opt(_dict(err).get("code")),
            _opt(_dict(err).get("message")) or "context API request failed",
        ) from error

def _map_slice(
    items: list[dict[str, Any]],
    *,
    limit: int,
    mapper: Callable[[dict[str, Any]], dict[str, Any] | None],
) -> list[dict[str, Any]]:
    mapped: list[dict[str, Any]] = []
    for item in items[:limit]:
        normalized = mapper(item)
        if normalized is not None:
            mapped.append(normalized)
    return mapped


def _normalize_bundle(payload: dict[str, Any], *, profile_id: str) -> dict[str, Any]:
    profile = _dict(payload.get("profile"))
    summary = _dict(payload.get("recordSummary"))
    selected = _dict(payload.get("selectedRecord"))

    def map_key_field(item: dict[str, Any]) -> dict[str, Any] | None:
        name, value = _opt(item.get("name")), _opt(item.get("value"))
        if not name or not value:
            return None
        return {
            "name": name,
            "value": value,
            "unit": _opt(item.get("unit")),
            "reference_range": _opt(item.get("referenceRange")),
        }

    def map_recent(item: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": _opt(item.get("id")),
            "title": _opt(item.get("title")),
            "record_date": _opt(item.get("recordDate")),
            "source_type": _opt(item.get("sourceType")),
            "parse_status": _opt(item.get("parseStatus")),
        }

    def map_trend(item: dict[str, Any]) -> dict[str, Any]:
        return {
            "record_id": _opt(item.get("recordId")),
            "record_date": _opt(item.get("recordDate")),
            "title": _opt(item.get("title")),
            "summary": _opt(item.get("summary")),
        }

    return {
        "context_status": _status(payload.get("contextStatus")),
        "disease_profile": {
            "id": _opt(profile.get("id")) or profile_id,
            "name": _opt(profile.get("name")),
            "record_count": int(profile.get("recordCount", 0) or 0),
            "latest_record_at": _opt(profile.get("latestRecordAt")),
        },
        "selected_record": None
        if not selected
        else {
            "id": _opt(selected.get("id")),
            "title": _opt(selected.get("title")),
            "record_date": _opt(selected.get("recordDate")),
            "source_type": _opt(selected.get("sourceType")),
            "parse_status": _opt(selected.get("parseStatus")),
        },
        "recent_records": _map_slice(_dict_list(payload.get("recentRecords")), limit=5, mapper=map_recent),
        "record_summary": {
            "summary": _opt(summary.get("summary")),
            "analysis": _opt(summary.get("analysis")),
            "key_fields": _map_slice(_dict_list(summary.get("keyFields")), limit=8, mapper=map_key_field),
        },
        "trend_summary": _map_slice(_dict_list(payload.get("trendSummary")), limit=3, mapper=map_trend),
        "warnings": [_txt(item) for item in payload.get("warnings", []) if _txt(item)],
    }


def _unavailable(profile_id: str, message: str, code: str | None = None) -> dict[str, Any]:
    return {
        "context_status": "unavailable",
        "disease_profile": {"id": profile_id, "name": None, "record_count": 0, "latest_record_at": None},
        "selected_record": None,
        "recent_records": [],
        "record_summary": {"summary": None, "analysis": None, "key_fields": []},
        "trend_summary": [],
        "warnings": [message],
        "error": {"code": code, "message": message},
    }

class DiseaseProfileContextClient:
    """Load compact disease profile context from Java internal API."""

    def __init__(
        self,
        *,
        base_url: str,
        context_path: str,
        timeout_seconds: float,
        api_key: str | None = None,
        api_key_header: str = "X-Internal-Api-Key",
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._api_prefix = "/" + context_path.strip("/") if context_path else ""
        self._timeout_seconds = timeout_seconds
        self._api_key = _txt(api_key)
        self._api_key_header = _txt(api_key_header) or "X-Internal-Api-Key"

    def fetch_context_bundle(
        self,
        *,
        disease_profile_id: str,
        record_id: str | None = None,
        patient_id: str | None = None,
    ) -> dict[str, Any]:
        profile_id = _txt(disease_profile_id)
        query = f"?{urllib.parse.urlencode({'recordId': _txt(record_id)})}" if _txt(record_id) else ""
        url = (
            f"{self._base_url}{self._api_prefix}/profiles/"
            f"{urllib.parse.quote(profile_id, safe='')}/context{query}"
        )
        headers = {"Accept": "application/json"}
        if self._api_key:
            headers[self._api_key_header] = self._api_key
        if patient_id and _txt(patient_id):
            headers["X-Patient-Id"] = _txt(patient_id)

        try:
            raw_payload = _http_get_json(url, headers=headers, timeout_seconds=self._timeout_seconds)
            payload = (
                raw_payload[1]
                if isinstance(raw_payload, tuple) and len(raw_payload) > 1 and isinstance(raw_payload[1], dict)
                else raw_payload
            )
            if not isinstance(payload, dict):
                payload = {}
            return _normalize_bundle(payload, profile_id=profile_id)
        except _HttpFailure as error:
            return _unavailable(profile_id, f"上下文加载失败：{error.message}", error.code or str(error.status_code))
        except Exception as error:  # pragma: no cover - defensive
            return _unavailable(profile_id, f"上下文加载失败：{error}", "CONTEXT_CLIENT_ERROR")
