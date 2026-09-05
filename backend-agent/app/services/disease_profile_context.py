"""Java 聚合疾病档案上下文端点的 HTTP 客户端。"""
from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Callable, Literal

from app.auth import AgentScope

ContextStatus = Literal["ready", "partial", "unavailable"]


@dataclass(slots=True)
class _HttpFailure(Exception):
    status_code: int
    code: str | None
    message: str


def _txt(value: Any) -> str:
    return str(value).strip() if value is not None else ""


def _opt(value: Any) -> str | None:
    text = _txt(value)
    return (text[:2000] + "…" if len(text) > 2000 else text) or None


def _dict(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


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
            _opt(_dict(err).get("message")) or "上下文 API 请求失败",
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
    patient_baseline = _dict(payload.get("patientBaseline"))

    def map_symptom(item: dict[str, Any]) -> dict[str, Any] | None:
        label = _opt(item.get("label"))
        if not label:
            return None
        return {
            "id": _opt(item.get("id")),
            "label": label,
            "value": _opt(item.get("value")),
            "unit": _opt(item.get("unit")),
            "alert_level": _opt(item.get("alertLevel")),
            "notes": _opt(item.get("notes")),
            "recorded_at": _opt(item.get("recordedAt")),
        }

    def map_key_field(item: dict[str, Any]) -> dict[str, Any] | None:
        name, value = _opt(item.get("name")), _opt(item.get("value"))
        if not name or not value:
            return None
        return {
            "name": name,
            "value": value,
            "unit": _opt(item.get("unit")),
            "reference_range": _opt(item.get("referenceRange")),
            "result_state": _opt(item.get("resultState")),
            "severity": _opt(item.get("severity")),
            "alert_level": _opt(item.get("alertLevel")),
            "is_abnormal": item.get("isAbnormal"),
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

    def map_medication(item: dict[str, Any]) -> dict[str, Any] | None:
        name = _opt(item.get("name"))
        if not name:
            return None
        return {
            "name": name,
            "dosage": _opt(item.get("dosage")),
            "frequency": _opt(item.get("frequency")),
            "purpose": _opt(item.get("purpose")),
        }

    def map_task(item: dict[str, Any]) -> dict[str, Any] | None:
        title = _opt(item.get("title"))
        if not title:
            return None
        return {
            "id": _opt(item.get("id")),
            "title": title,
            "due_date": _opt(item.get("dueDate")),
            "priority": _opt(item.get("priority")),
            "status": _opt(item.get("status")),
            "notes": _opt(item.get("notes")),
            "disease_profile_id": _opt(item.get("diseaseProfileId")),
            "record_id": _opt(item.get("recordId")),
            "created_at": _opt(item.get("createdAt")),
        }

    def map_risk_signal(item: dict[str, Any]) -> dict[str, Any] | None:
        title = _opt(item.get("title"))
        if not title:
            return None
        return {
            "severity": _opt(item.get("severity")),
            "title": title,
            "detail": _opt(item.get("detail")),
            "recommended_action": _opt(item.get("recommendedAction")),
        }

    def map_evidence(item: dict[str, Any]) -> dict[str, Any] | None:
        title = _opt(item.get("title"))
        if not title:
            return None
        return {
            "type": _opt(item.get("type")),
            "title": title,
            "detail": _opt(item.get("detail")),
            "source": _opt(item.get("source")),
            "confidence": _opt(item.get("confidence")),
            "nature": _opt(item.get("nature")),
        }

    def map_ledger_evidence(item: dict[str, Any]) -> dict[str, Any] | None:
        evidence_id = _opt(item.get("evidenceId"))
        summary_text = _opt(item.get("summary"))
        if not evidence_id or not summary_text:
            return None
        return {
            "evidence_id": evidence_id,
            "category": _opt(item.get("category")),
            "summary": summary_text,
            "source_type": _opt(item.get("sourceType")),
            "source_ref": _opt(item.get("sourceRef")),
            "observed_at": _opt(item.get("observedAt")),
            "updated_at": _opt(item.get("updatedAt")),
            "verification_status": _opt(item.get("verificationStatus")),
        }

    def map_ultrasound_evidence(item: dict[str, Any]) -> dict[str, Any] | None:
        text = _opt(item.get("text"))
        if not text:
            return None
        return {
            "record_id": _opt(item.get("recordId")),
            "record_date": _opt(item.get("recordDate")),
            "label": _opt(item.get("label")),
            "text": text,
        }

    def map_ultrasound_history(item: dict[str, Any]) -> dict[str, Any] | None:
        record_id = _opt(item.get("recordId"))
        if not record_id:
            return None
        return {
            "record_id": record_id,
            "record_date": _opt(item.get("recordDate")),
            "title": _opt(item.get("title")),
            "summary": _opt(item.get("summary")),
        }

    def map_ultrasound_finding(item: dict[str, Any]) -> dict[str, Any] | None:
        module = _opt(item.get("module"))
        if not module:
            return None
        return {
            "module": module,
            "current_value": _opt(item.get("currentValue")),
            "previous_value": _opt(item.get("previousValue")),
            "current_status": _opt(item.get("currentStatus")),
            "previous_status": _opt(item.get("previousStatus")),
            "trend_status": _opt(item.get("trendStatus")),
            "evidence_level": _opt(item.get("evidenceLevel")),
            "explanation": _opt(item.get("explanation")),
        }

    def map_ultrasound_risk(item: dict[str, Any]) -> dict[str, Any] | None:
        name = _opt(item.get("name"))
        if not name:
            return None
        return {
            "name": name,
            "level": _opt(item.get("level")),
            "summary": _opt(item.get("summary")),
            "evidence": [_txt(value) for value in item.get("evidence", []) if _txt(value)],
            "missing_inputs": [_txt(value) for value in item.get("missingInputs", []) if _txt(value)],
        }

    def map_ultrasound_missing(item: dict[str, Any]) -> dict[str, Any] | None:
        name = _opt(item.get("name"))
        if not name:
            return None
        return {
            "name": name,
            "reason": _opt(item.get("reason")),
            "category": _opt(item.get("category")),
        }

    ultrasound_follow_up = _dict(summary.get("ultrasoundFollowUp"))

    def map_pending_memory(item: dict[str, Any]) -> dict[str, Any] | None:
        field_path = _opt(item.get("fieldPath"))
        value_text = _opt(item.get("valueText"))
        if not field_path or not value_text:
            return None
        return {
            "id": _opt(item.get("id")),
            "memory_type": _opt(item.get("memoryType")),
            "field_path": field_path,
            "value_text": value_text,
            "evidence_text": _opt(item.get("evidenceText")),
            "risk_level": _opt(item.get("riskLevel")),
            "confidence": item.get("confidence"),
            "status": _opt(item.get("status")),
            "source_type": _opt(item.get("sourceType")),
            "source_ref": _opt(item.get("sourceRef")),
            "valid_from": _opt(item.get("validFrom")),
            "valid_to": _opt(item.get("validTo")),
            "is_current": item.get("isCurrent"),
            "supersedes_memory_id": _opt(item.get("supersedesMemoryId")),
        }

    return {
        "context_status": _status(payload.get("contextStatus")),
        "context_revision": _opt(payload.get("contextRevision")),
        "generated_at": _opt(payload.get("generatedAt")),
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
            "ultrasound_follow_up": None
            if not ultrasound_follow_up
            else {
                "mode": _opt(ultrasound_follow_up.get("mode")),
                "change_status": _opt(ultrasound_follow_up.get("changeStatus")),
                "summary": _opt(ultrasound_follow_up.get("summary")),
                "action_level": _opt(ultrasound_follow_up.get("actionLevel")),
                "action_suggestion": _opt(ultrasound_follow_up.get("actionSuggestion")),
                "patient_summary": _opt(ultrasound_follow_up.get("patientSummary")),
                "clinical_summary": _opt(ultrasound_follow_up.get("clinicalSummary")),
                "confidence_level": _opt(ultrasound_follow_up.get("confidenceLevel")),
                "finding_rows": _map_slice(
                    _dict_list(ultrasound_follow_up.get("findingRows")),
                    limit=8,
                    mapper=map_ultrasound_finding,
                ),
                "risk_modules": _map_slice(
                    _dict_list(ultrasound_follow_up.get("riskModules")),
                    limit=4,
                    mapper=map_ultrasound_risk,
                ),
                "missing_inputs": _map_slice(
                    _dict_list(ultrasound_follow_up.get("missingInputs")),
                    limit=12,
                    mapper=map_ultrasound_missing,
                ),
                "next_questions_for_doctor": [
                    _txt(value)
                    for value in ultrasound_follow_up.get("nextQuestionsForDoctor", [])
                    if _txt(value)
                ][:6],
                "current_evidence": _map_slice(
                    _dict_list(ultrasound_follow_up.get("currentEvidence")),
                    limit=3,
                    mapper=map_ultrasound_evidence,
                ),
                "previous_evidence": _map_slice(
                    _dict_list(ultrasound_follow_up.get("previousEvidence")),
                    limit=3,
                    mapper=map_ultrasound_evidence,
                ),
                "history": _map_slice(
                    _dict_list(ultrasound_follow_up.get("history")),
                    limit=4,
                    mapper=map_ultrasound_history,
                ),
            },
        },
        "trend_summary": _map_slice(_dict_list(payload.get("trendSummary")), limit=3, mapper=map_trend),
        "patient_baseline": {
            "diagnosed_conditions": [_txt(item) for item in patient_baseline.get("diagnosedConditions", []) if _txt(item)],
            "allergies": [_txt(item) for item in patient_baseline.get("allergies", []) if _txt(item)],
            "abnormal_baseline": [_txt(item) for item in patient_baseline.get("abnormalBaseline", []) if _txt(item)],
            "doctor_instructions": _opt(patient_baseline.get("doctorInstructions")),
            "recent_symptoms": _map_slice(
                _dict_list(patient_baseline.get("recentSymptoms")),
                limit=4,
                mapper=map_symptom,
            ),
        },
        "current_medications": _map_slice(_dict_list(payload.get("currentMedications")), limit=8, mapper=map_medication),
        "care_goals": [_txt(item) for item in payload.get("careGoals", []) if _txt(item)],
        "personal_context": [_txt(item) for item in payload.get("personalContext", []) if _txt(item)],
        "follow_up_tasks": _map_slice(_dict_list(payload.get("followUpTasks")), limit=5, mapper=map_task),
        "red_flag_signals": _map_slice(_dict_list(payload.get("redFlagSignals")), limit=4, mapper=map_risk_signal),
        "evidence_refs": _map_slice(_dict_list(payload.get("evidenceRefs")), limit=6, mapper=map_evidence),
        "evidence_ledger": _map_slice(
            _dict_list(payload.get("evidenceLedger")), limit=32, mapper=map_ledger_evidence
        ),
        "confirmed_memories": _map_slice(
            _dict_list(payload.get("confirmedMemories")), limit=10, mapper=map_pending_memory
        ),
        "pending_memories": _map_slice(_dict_list(payload.get("pendingMemories")), limit=5, mapper=map_pending_memory),
        "warnings": [_txt(item) for item in payload.get("warnings", []) if _txt(item)],
    }


def _unavailable(profile_id: str, message: str, code: str | None = None) -> dict[str, Any]:
    return {
        "context_status": "unavailable",
        "context_revision": None,
        "generated_at": None,
        "disease_profile": {"id": profile_id, "name": None, "record_count": 0, "latest_record_at": None},
        "selected_record": None,
        "recent_records": [],
        "record_summary": {"summary": None, "analysis": None, "key_fields": [], "ultrasound_follow_up": None},
        "trend_summary": [],
        "patient_baseline": {
            "diagnosed_conditions": [],
            "allergies": [],
            "abnormal_baseline": [],
            "doctor_instructions": None,
            "recent_symptoms": [],
        },
        "current_medications": [],
        "care_goals": [],
        "personal_context": [],
        "follow_up_tasks": [],
        "red_flag_signals": [],
        "evidence_refs": [],
        "evidence_ledger": [],
        "confirmed_memories": [],
        "pending_memories": [],
        "warnings": [message],
        "error": {"code": code, "message": message},
    }


class DiseaseProfileContextClient:
    """从 Java 内部 API 加载紧凑疾病档案上下文。"""

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
        scope: AgentScope | None = None,
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
        if scope is not None:
            headers.update(scope.internal_headers())

        try:
            payload = _http_get_json(url, headers=headers, timeout_seconds=self._timeout_seconds)
            if not isinstance(payload, dict):
                payload = {}
            return _normalize_bundle(payload, profile_id=profile_id)
        except _HttpFailure as error:
            return _unavailable(profile_id, f"上下文加载失败：{error.message}", error.code or str(error.status_code))
        except Exception as error:  # pragma: no cover - 防御性代码
            return _unavailable(profile_id, f"上下文加载失败：{error}", "CONTEXT_CLIENT_ERROR")
