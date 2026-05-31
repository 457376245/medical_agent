"""从对话中抽取患者长期画像候选并提交给 Java 账本。"""

from __future__ import annotations

import json
import logging
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

from app.utils import normalize_openai_base_url

LOGGER = logging.getLogger(__name__)

_SUPPORTED_FIELD_PATHS = {
    "patientBaseline.diagnosedConditions",
    "patientBaseline.allergies",
    "patientBaseline.abnormalBaseline",
    "patientBaseline.doctorInstructions",
    "patientBaseline.recentSymptoms",
    "currentMedications",
    "careGoals",
    "redFlagNotes",
    "personalContext",
    "followUpTasks",
}

_EXTRACTION_SCHEMA: dict[str, Any] = {
    "name": "patient_memory_candidates",
    "strict": True,
    "schema": {
        "type": "object",
        "properties": {
            "entries": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "memoryType": {"type": "string"},
                        "fieldPath": {"type": "string"},
                        "valueText": {"type": ["string", "null"]},
                        "value": {
                            "anyOf": [
                                {"type": "object", "additionalProperties": True},
                                {"type": "null"},
                            ]
                        },
                        "evidenceText": {"type": ["string", "null"]},
                        "confidence": {"type": "number"},
                        "riskLevel": {"type": "string"},
                    },
                    "required": [
                        "memoryType",
                        "fieldPath",
                        "valueText",
                        "value",
                        "evidenceText",
                        "confidence",
                        "riskLevel",
                    ],
                    "additionalProperties": False,
                },
            }
        },
        "required": ["entries"],
        "additionalProperties": False,
    },
}


class PatientMemoryExtractionService:
    """LLM 抽取器 + Java 内部 API 提交器。"""

    def __init__(
        self,
        *,
        enabled: bool,
        openai_base_url: str,
        openai_api_key: str,
        model: str,
        java_base_url: str,
        java_context_path: str,
        timeout_seconds: float,
        java_api_key: str | None = None,
        java_api_key_header: str = "X-Internal-Api-Key",
    ) -> None:
        self._enabled = enabled
        self._openai_base_url = normalize_openai_base_url(openai_base_url)
        self._openai_api_key = openai_api_key.strip()
        self._model = model.strip()
        self._java_base_url = java_base_url.rstrip("/")
        api_prefix = "/" + java_context_path.strip("/") if java_context_path else ""
        self._submit_url = f"{self._java_base_url}{api_prefix}/patient-memories"
        self._timeout_seconds = timeout_seconds
        self._java_api_key = (java_api_key or "").strip()
        self._java_api_key_header = java_api_key_header.strip() or "X-Internal-Api-Key"

    @property
    def configured(self) -> bool:
        return bool(
            self._enabled
            and self._openai_base_url
            and self._openai_api_key
            and self._model
            and self._java_base_url
        )

    def extract_and_submit(
        self,
        *,
        thread_id: str,
        turn_id: str | None,
        user_message: str,
        assistant_message: str,
        metadata: dict[str, Any],
    ) -> int:
        """抽取并提交画像候选；返回提交条数。失败由调用方记录，不抛给聊天流。"""
        if not self.configured:
            return 0
        disease_profile_id = _text(metadata.get("disease_profile_id"))
        if not disease_profile_id:
            return 0
        entries = self._extract_entries(
            user_message=user_message,
            assistant_message=assistant_message,
            metadata=metadata,
        )
        if not entries:
            return 0
        payload = {
            "conversationThreadId": thread_id,
            "turnId": turn_id,
            "diseaseProfileId": disease_profile_id,
            "recordId": _text(metadata.get("record_id")) or None,
            "entries": entries,
        }
        return self._submit_payload(payload, patient_id=_text(metadata.get("patient_id")))

    def _extract_entries(
        self,
        *,
        user_message: str,
        assistant_message: str,
        metadata: dict[str, Any],
    ) -> list[dict[str, Any]]:
        prompt = {
            "userMessage": user_message,
            "assistantMessage": assistant_message,
            "metadata": {
                "diseaseProfileId": _text(metadata.get("disease_profile_id")),
                "recordId": _text(metadata.get("record_id")),
                "workflow": _text(metadata.get("workflow")),
            },
        }
        messages = [
            {
                "role": "system",
                "content": (
                    "你是医疗长期画像记忆抽取器。只抽取用户明确提供或明确确认的信息，"
                    "不要把助手建议、推测、教育性解释当成患者事实。"
                    "诊断、过敏、当前用药、医生交代事项属于 HIGH 风险；"
                    "随访任务和异常基线属于 MEDIUM；症状、健康目标、红旗提醒、生活方式、照护情况、"
                    "表达偏好等 personalContext 可为 LOW。"
                    "fieldPath 只能使用允许集合："
                    + "、".join(sorted(_SUPPORTED_FIELD_PATHS))
                    + "。如果没有可靠候选，返回空 entries。"
                ),
            },
            {
                "role": "user",
                "content": json.dumps(prompt, ensure_ascii=False),
            },
        ]
        raw = self._invoke_json(messages)
        payload = _load_json_object(raw)
        raw_entries = payload.get("entries")
        if not isinstance(raw_entries, list):
            return []
        entries = [_normalize_entry(item) for item in raw_entries]
        return [item for item in entries if item]

    def _invoke_json(self, messages: list[dict[str, Any]]) -> str:
        payload = {
            "model": self._model,
            "temperature": 0,
            "messages": messages,
            "response_format": {
                "type": "json_schema",
                "json_schema": _EXTRACTION_SCHEMA,
            },
        }
        stream_payload = {**payload, "stream": True}
        request = urllib.request.Request(
            url=f"{self._openai_base_url}/chat/completions",
            data=json.dumps(stream_payload).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {self._openai_api_key}",
                "Content-Type": "application/json",
                "Accept": "text/event-stream",
            },
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=self._timeout_seconds) as response:
            return _extract_chat_content(response.read().decode("utf-8") or "{}")

    def _submit_payload(self, payload: dict[str, Any], *, patient_id: str | None) -> int:
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if self._java_api_key:
            headers[self._java_api_key_header] = self._java_api_key
        if patient_id:
            headers["X-Patient-Id"] = patient_id
        request = urllib.request.Request(
            url=self._submit_url,
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers=headers,
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self._timeout_seconds) as response:
                body = json.loads(response.read().decode("utf-8") or "{}")
        except urllib.error.HTTPError as error:
            raw = error.read().decode("utf-8", errors="ignore")
            raise RuntimeError(f"patient memory submit failed: {error.code} {raw[:300]}") from error
        data = body.get("data") if isinstance(body, dict) else {}
        memories = data.get("memories") if isinstance(data, dict) else []
        return len(memories) if isinstance(memories, list) else 0


def _normalize_entry(raw: Any) -> dict[str, Any] | None:
    if not isinstance(raw, dict):
        return None
    field_path = _text(raw.get("fieldPath"))
    if field_path not in _SUPPORTED_FIELD_PATHS:
        return None
    value_text = _text(raw.get("valueText"))
    value = raw.get("value") if isinstance(raw.get("value"), dict) else None
    if not value_text and not value:
        return None
    return {
        "memoryType": _text(raw.get("memoryType")) or _default_type(field_path),
        "fieldPath": field_path,
        "valueText": value_text,
        "value": value,
        "evidenceText": _text(raw.get("evidenceText")),
        "confidence": _confidence(raw.get("confidence")),
        "riskLevel": _risk(raw.get("riskLevel"), field_path),
    }


def _default_type(field_path: str) -> str:
    if field_path == "currentMedications":
        return "MEDICATION"
    if field_path == "followUpTasks":
        return "FOLLOW_UP"
    if field_path == "patientBaseline.recentSymptoms":
        return "SYMPTOM"
    if field_path == "personalContext":
        return "PERSONAL_CONTEXT"
    return "CARE_PROFILE"


def _risk(value: Any, field_path: str) -> str:
    normalized = _text(value).upper()
    if normalized in {"LOW", "MEDIUM", "HIGH"}:
        return normalized
    if field_path in {
        "patientBaseline.diagnosedConditions",
        "patientBaseline.allergies",
        "currentMedications",
        "patientBaseline.doctorInstructions",
    }:
        return "HIGH"
    if field_path in {"followUpTasks", "patientBaseline.abnormalBaseline"}:
        return "MEDIUM"
    return "LOW"


def _confidence(value: Any) -> float:
    try:
        numeric = float(value)
    except (TypeError, ValueError):
        return 0.5
    return max(0.0, min(1.0, round(numeric, 4)))


def _text(value: Any) -> str:
    return str(value).strip() if value is not None else ""


def _load_json_object(raw_output: str) -> dict[str, Any]:
    cleaned = raw_output.strip()
    if cleaned.startswith("```"):
        lines = cleaned.splitlines()[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        cleaned = "\n".join(lines).strip()
    try:
        parsed = json.loads(cleaned)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def _extract_chat_content(raw_body: str) -> str:
    if raw_body.lstrip().startswith("data:"):
        return _extract_stream_content(raw_body)
    try:
        body = json.loads(raw_body)
    except json.JSONDecodeError:
        return "{}"
    choices = body.get("choices")
    if not isinstance(choices, list) or not choices:
        return "{}"
    message = choices[0].get("message") if isinstance(choices[0], dict) else {}
    content = message.get("content") if isinstance(message, dict) else ""
    return content if isinstance(content, str) else "{}"


def _extract_stream_content(raw_body: str) -> str:
    content_parts: list[str] = []
    for line in raw_body.splitlines():
        line = line.strip()
        if not line.startswith("data:"):
            continue
        data = line[5:].strip()
        if not data or data == "[DONE]":
            continue
        try:
            chunk = json.loads(data)
        except json.JSONDecodeError:
            continue
        choices = chunk.get("choices")
        if not isinstance(choices, list) or not choices:
            continue
        delta = choices[0].get("delta") if isinstance(choices[0], dict) else {}
        if isinstance(delta, dict) and isinstance(delta.get("content"), str):
            content_parts.append(delta["content"])
    return "".join(content_parts) or "{}"
