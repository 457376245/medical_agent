from __future__ import annotations

import json
import logging
import os
import socket
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from app.prompts.provider import (
    CATEGORY_CLASSIFICATION_SYSTEM_PROMPT,
    GENERATE_SYSTEM_PROMPT,
    MEDICATION_PLAN_TASK_PROMPT,
    PARSE_JSON_ONLY_SUFFIX,
    PARSE_SYSTEM_PROMPT,
    REPORT_ANALYSIS_TASK_PROMPT,
    SUMMARY_TASK_PROMPT,
)
from app.providers.document import DocumentParser
from app.providers.storage import OSSStorageService
from app.utils import normalize_openai_base_url, read_float_env, read_int_env, to_bool


LOGGER = logging.getLogger(__name__)

_PARSE_OUTPUT_SCHEMA: dict[str, Any] = {
    "name": "medical_parse_output",
    "strict": True,
    "schema": {
        "type": "object",
        "properties": {
            "fields": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "value": {"type": "string"},
                        "unit": {"type": ["string", "null"]},
                        "referenceRange": {"type": ["string", "null"]},
                        "standardCode": {"type": ["string", "null"]},
                        "confidence": {"type": "number"},
                        "evidence": {
                            "anyOf": [
                                {
                                    "type": "object",
                                    "properties": {
                                        "sourceFile": {"type": "string"},
                                        "page": {"type": ["integer", "null"]},
                                        "snippet": {"type": ["string", "null"]},
                                    },
                                    "required": ["sourceFile", "page", "snippet"],
                                    "additionalProperties": False,
                                },
                                {"type": "null"},
                            ]
                        },
                    },
                    "required": [
                        "name", "value", "unit", "referenceRange",
                        "standardCode", "confidence", "evidence",
                    ],
                    "additionalProperties": False,
                },
            },
            "reportDate": {"type": ["string", "null"]},
        },
        "required": ["fields", "reportDate"],
        "additionalProperties": False,
    },
}


class ParseEvidence(BaseModel):
    """解析字段来源证据模型。"""
    model_config = ConfigDict(populate_by_name=True)

    source_file: str = Field(alias="sourceFile")
    page: int | None = None
    snippet: str | None = None


class ParseField(BaseModel):
    """解析字段模型。"""
    model_config = ConfigDict(populate_by_name=True)

    name: str
    value: str
    unit: str | None = None
    reference_range: str | None = Field(default=None, alias="referenceRange")
    standard_code: str | None = Field(default=None, alias="standardCode")
    confidence: float = Field(ge=0, le=1)
    evidence: ParseEvidence | None = None


class ParseAgentOutput(BaseModel):
    """解析输出模型。"""
    model_config = ConfigDict(populate_by_name=True)

    fields: list[ParseField] = Field(default_factory=list)
    report_date: str | None = Field(default=None, alias="reportDate")


class LLMError(Exception):
    """LLM 交互失败时抛出的异常。"""

    def __init__(self, message: str, *, code: str) -> None:
        super().__init__(message)
        self.code = code


class LLMService:
    """管理提示词构建和 LLM 调用（基于 OpenAI chat completions）。"""

    def __init__(
        self,
        storage: OSSStorageService | None = None,
        document: DocumentParser | None = None,
    ) -> None:
        self._storage = storage or OSSStorageService()
        self._document = document or DocumentParser()

        self._openai_base_url = normalize_openai_base_url(
            os.getenv("OPENAI_BASE_URL", "")
        )
        self._openai_api_key = os.getenv("OPENAI_API_KEY", "").strip()
        self._openai_parse_model = os.getenv("OPENAI_PARSE_MODEL", "gpt-5.4").strip()
        self._openai_generate_model = os.getenv(
            "OPENAI_GENERATE_MODEL", "gpt-5.4"
        ).strip()
        self._openai_vision_model = os.getenv("OPENAI_VISION_MODEL", "gpt-5.4").strip()
        self._openai_fallback_model = os.getenv(
            "OPENAI_FALLBACK_MODEL", "gpt-5.4"
        ).strip()
        self._openai_temperature = read_float_env("OPENAI_TEMPERATURE", 0.0, 0.0)
        self._openai_request_timeout_seconds = read_float_env(
            "OPENAI_REQUEST_TIMEOUT_SECONDS", 90.0, 1.0
        )
        self._openai_sdk_retries = read_int_env("OPENAI_SDK_RETRIES", 2, 0)
        self._openai_trust_env = to_bool(os.getenv("OPENAI_TRUST_ENV", "false"))
        self._openai_proxy = os.getenv("OPENAI_PROXY", "").strip()
        self._openai_retry_with_env_proxy = to_bool(
            os.getenv("OPENAI_RETRY_WITH_ENV_PROXY", "true")
        )
        self._use_structured_output = to_bool(
            os.getenv("OPENAI_STRUCTURED_OUTPUT", "true")
        )

    def model_for_attempt(self, operation: str, attempt: int) -> str:
        primary = (
            self._openai_generate_model
            if operation == "generate"
            else self._openai_parse_model
        )
        if attempt == 1 or not self._openai_fallback_model:
            return primary
        return self._openai_fallback_model

    def parse(
        self, payload: dict[str, Any], model_name: str, attempt: int
    ) -> dict[str, Any]:
        """解析报告内容并提取结构化字段。"""
        asset_refs = payload.get("assetRefs")
        if not isinstance(asset_refs, list) or not asset_refs:
            raise ValueError("BIZ_MISSING_ASSET_REFS")

        first_asset = asset_refs[0] if isinstance(asset_refs[0], dict) else {}
        object_key = str(first_asset.get("objectKey", "")).strip().lstrip("/")
        file_type = str(first_asset.get("fileType", "")).upper()
        if not object_key:
            raise ValueError("BIZ_MISSING_OBJECT_KEY")

        content = self._storage.download_bytes(object_key)
        user_content = self._document.build_parse_content(file_type, object_key, content)
        del content

        effective_model = self._model_for_parse_content(
            requested_model=model_name,
            user_content=user_content,
            attempt=attempt,
        )
        raw_output = self._invoke_parse_content(
            user_content=user_content,
            model_name=effective_model,
            attempt=attempt,
        )
        structured = self._coerce_structured(raw_output)

        normalized_fields = [
            _normalize_field(
                field.model_dump(by_alias=True, exclude_none=True),
                object_key,
            )
            for field in structured.fields
        ]
        fields = [item for item in normalized_fields if item]
        if not fields:
            raise LLMError(
                "LLM 返回了无效的字段", code="BIZ_EMPTY_PARSE_RESULT"
            )

        confidence = _average_confidence(fields)
        meta = {
            "provider": "openai-compatible",
            "framework": "chat-completions",
            "model": effective_model,
        }
        result: dict[str, Any] = {
            "structuredResult": {
                "schemaVersion": "v1",
                "fields": fields,
                "meta": meta,
            },
            "confidence": confidence,
            "modelMeta": meta,
        }

        # 提取报告日期（如果有）
        report_date = structured.report_date
        if report_date:
            result["reportDate"] = report_date

        return result

    def generate(
        self, payload: dict[str, Any], model_name: str, attempt: int
    ) -> dict[str, Any]:
        """生成报告摘要或分析内容。"""
        output_type = str(payload.get("type", "SUMMARY")).upper()
        system_prompt = GENERATE_SYSTEM_PROMPT
        if output_type == "MED_PLAN":
            task_prompt = MEDICATION_PLAN_TASK_PROMPT
        elif output_type == "REPORT_ANALYSIS":
            task_prompt = REPORT_ANALYSIS_TASK_PROMPT
        else:
            task_prompt = SUMMARY_TASK_PROMPT

        analysis_context = (
            payload.get("analysisContext")
            if isinstance(payload.get("analysisContext"), dict)
            else {}
        )
        safe_context = {
            "recordId": payload.get("recordId"),
            "type": output_type,
            "traceId": payload.get("traceId"),
            "analysisContext": analysis_context
            if output_type == "REPORT_ANALYSIS"
            else {},
        }
        user_prompt = (
            f"{task_prompt}\n"
            "Context (JSON): "
            f"{json.dumps(safe_context, ensure_ascii=False)}"
        )
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ]

        text = self._invoke_text(messages=messages, model_name=model_name, attempt=attempt)
        if not text:
            raise LLMError("LLM 返回了空内容", code="BIZ_EMPTY_GENERATION")

        return {
            "type": output_type,
            "content": text,
            "modelMeta": {
                "provider": "openai-compatible",
                "framework": "chat-completions",
                "model": model_name,
            },
        }

    def classify_report_category(
        self,
        fields: list[dict[str, Any]],
        existing_categories: list[str],
    ) -> str:
        """将解析后的医疗报告分类（最多5个汉字）。"""
        fields_text = "\n".join(
            f"- {f.get('name', '')}: {f.get('value', '')} {f.get('unit', '') or ''}".strip()
            for f in fields[:30]
        )
        categories_text = (
            "、".join(existing_categories) if existing_categories else "（暂无）"
        )

        messages = [
            {
                "role": "system",
                "content": CATEGORY_CLASSIFICATION_SYSTEM_PROMPT,
            },
            {
                "role": "user",
                "content": (
                    f"已有报告分类：{categories_text}\n\n"
                    f"报告检测字段：\n{fields_text}\n\n"
                    "请判断该报告应归入哪个分类。"
                    "如果可以归入已有分类，请直接返回该分类名称。"
                    "如果不适合任何已有分类，请生成一个新的分类名（≤5个汉字）。"
                    "只返回分类名称，不要返回任何其他内容。"
                ),
            },
        ]
        text = self._invoke_text(
            messages=messages,
            model_name=self._openai_generate_model,
            attempt=1,
        )
        classified = text.strip().strip("\"'""''")
        if len(classified) > 5:
            classified = classified[:5]
        return classified

    def _model_for_parse_content(
        self,
        *,
        requested_model: str,
        user_content: list[dict[str, Any]],
        attempt: int,
    ) -> str:
        if attempt > 1:
            return requested_model
        if self._document.contains_visual_parts(user_content):
            return self._openai_vision_model or requested_model
        return requested_model

    def _invoke_parse_content(
        self,
        *,
        user_content: list[dict[str, Any]],
        model_name: str,
        attempt: int,
    ) -> str:
        structured = self._use_structured_output
        system_content = PARSE_SYSTEM_PROMPT
        if not structured:
            system_content += PARSE_JSON_ONLY_SUFFIX
        messages = [
            {"role": "system", "content": system_content},
            {"role": "user", "content": user_content},
        ]
        response_format = (
            {"type": "json_schema", "json_schema": _PARSE_OUTPUT_SCHEMA}
            if structured
            else None
        )
        return self._invoke_text(
            messages=messages,
            model_name=model_name,
            attempt=attempt,
            response_format=response_format,
        )

    def _invoke_text(
        self,
        *,
        messages: list[dict[str, Any]],
        model_name: str,
        attempt: int,
        response_format: dict[str, Any] | None = None,
    ) -> str:
        payload: dict[str, Any] = {
            "model": model_name,
            "temperature": self._openai_temperature,
            "messages": messages,
        }
        if response_format is not None:
            payload["response_format"] = response_format
        status_code, body = self._send_chat_completion_request(
            payload=payload,
            attempt=attempt,
        )
        if status_code >= 400:
            raise self._to_http_error(status_code, body)
        content = _extract_message_content(body)
        if not content:
            LOGGER.warning(
                "LLM 返回空内容: status=%s body=%s",
                status_code,
                json.dumps(body, ensure_ascii=False)[:500],
            )
            raise LLMError(
                "LLM 返回了空内容",
                code="BIZ_EMPTY_LLM_RESPONSE",
            )
        return content.strip()

    def _coerce_structured(self, raw_output: str) -> ParseAgentOutput:
        try:
            parsed = _load_json_object(raw_output)
        except ValueError as exc:
            raise LLMError(
                f"解析 LLM 输出失败: {exc}",
                code="BIZ_INVALID_LLM_OUTPUT",
            ) from exc
        return ParseAgentOutput.model_validate(parsed)

    def _send_chat_completion_request(
        self, *, payload: dict[str, Any], attempt: int
    ) -> tuple[int, dict[str, Any]]:
        self._ensure_configured()
        url = f"{self._openai_base_url}/chat/completions"

        # 使用流式模式处理非流式返回空内容的 LLM 服务
        stream_payload = {**payload, "stream": True}

        request = urllib.request.Request(
            url=url,
            data=json.dumps(stream_payload).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {self._openai_api_key}",
                "Content-Type": "application/json",
                "Accept": "text/event-stream",
            },
            method="POST",
        )
        opener = self._build_opener(attempt)
        try:
            with opener.open(request, timeout=self._openai_request_timeout_seconds) as response:
                raw_body = response.read().decode("utf-8")
                parsed_body = self._parse_streaming_response(raw_body)
                return response.status, parsed_body
        except urllib.error.HTTPError as exc:
            raw_body = exc.read().decode("utf-8", errors="replace")
            # 优先尝试解析流式响应
            if raw_body.startswith("data:"):
                parsed_body = self._parse_streaming_response(raw_body)
                return exc.code, parsed_body
            try:
                parsed_body = json.loads(raw_body) if raw_body else {}
            except json.JSONDecodeError:
                parsed_body = {"raw_error": raw_body}
            return exc.code, parsed_body
        except urllib.error.URLError as exc:
            reason = exc.reason
            if isinstance(reason, TimeoutError | socket.timeout):
                raise TimeoutError(f"OpenAI 请求超时: {reason}") from exc
            raise ConnectionError(f"OpenAI 请求失败: {reason}") from exc

    def _parse_streaming_response(self, raw_body: str) -> dict[str, Any]:
        """解析 SSE 流式响应并重建完整响应。"""
        content_parts: list[str] = []
        model = ""
        usage: dict[str, Any] = {}

        for line in raw_body.split("\n"):
            line = line.strip()
            if not line or not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if data == "[DONE]":
                break
            try:
                chunk = json.loads(data)
            except json.JSONDecodeError:
                continue

            if not model and chunk.get("model"):
                model = chunk["model"]

            choices = chunk.get("choices", [])
            if choices:
                delta = choices[0].get("delta", {})
                if isinstance(delta.get("content"), str):
                    content_parts.append(delta["content"])

            if chunk.get("usage"):
                usage = chunk["usage"]

        return {
            "choices": [{
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": "".join(content_parts),
                },
                "finish_reason": "stop",
            }],
            "model": model,
            "usage": usage,
        }

    def _build_opener(self, attempt: int) -> urllib.request.OpenerDirector:
        trust_env = self._openai_trust_env
        if (
            self._openai_retry_with_env_proxy
            and not self._openai_proxy
            and attempt >= 2
        ):
            trust_env = (
                not self._openai_trust_env
                if attempt % 2 == 0
                else self._openai_trust_env
            )

        if self._openai_proxy:
            return urllib.request.build_opener(
                urllib.request.ProxyHandler(
                    {
                        "http": self._openai_proxy,
                        "https": self._openai_proxy,
                    }
                )
            )
        if not trust_env:
            return urllib.request.build_opener(urllib.request.ProxyHandler({}))
        return urllib.request.build_opener()

    def _ensure_configured(self) -> None:
        if not self._openai_base_url:
            raise LLMError(
                "OpenAI base URL 未配置",
                code="BIZ_OPENAI_NOT_CONFIGURED",
            )
        if not self._openai_api_key:
            raise LLMError(
                "OpenAI API key 未配置",
                code="BIZ_OPENAI_NOT_CONFIGURED",
            )

    @staticmethod
    def _to_http_error(status_code: int, body: dict[str, Any]) -> LLMError:
        message = _extract_error_message(body) or json.dumps(body, ensure_ascii=False)
        if status_code in {400, 404}:
            return LLMError(message, code="BIZ_LLM_REQUEST_INVALID")
        if status_code in {401, 403}:
            return LLMError(message, code="BIZ_LLM_UNAUTHORIZED")
        if status_code == 408:
            return LLMError(message, code="EXT_TIMEOUT")
        return LLMError(message, code="EXT_PROVIDER_UNAVAILABLE")


def _extract_message_content(body: dict[str, Any]) -> str:
    """从响应体中提取消息内容。"""
    choices = body.get("choices")
    if not isinstance(choices, list) or not choices:
        return ""
    first_choice = choices[0]
    if not isinstance(first_choice, dict):
        return ""
    message = first_choice.get("message")
    if not isinstance(message, dict):
        return ""
    content = message.get("content")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict) and isinstance(item.get("text"), str):
                parts.append(str(item["text"]))
        return "".join(parts)
    return ""


def _extract_error_message(body: dict[str, Any]) -> str:
    """从响应体中提取错误消息。"""
    error = body.get("error")
    if isinstance(error, dict):
        message = error.get("message")
        if isinstance(message, str) and message.strip():
            return message.strip()
    message = body.get("message")
    if isinstance(message, str) and message.strip():
        return message.strip()
    return ""


def _load_json_object(raw_output: str) -> dict[str, Any]:
    """加载 JSON 对象，支持 markdown 代码块。"""
    cleaned = raw_output.strip()
    if cleaned.startswith("```"):
        lines = cleaned.splitlines()
        if lines:
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        cleaned = "\n".join(lines).strip()

    candidates = [cleaned]
    start = cleaned.find("{")
    end = cleaned.rfind("}")
    if start != -1 and end != -1 and start < end:
        candidates.append(cleaned[start : end + 1])

    for candidate in candidates:
        if not candidate:
            continue
        try:
            parsed = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        if isinstance(parsed, dict):
            return parsed
    raise ValueError("响应不是有效的 JSON 对象")


def _normalize_field(field: dict[str, Any], object_key: str) -> dict[str, Any]:
    """规范化解析字段。"""
    name = str(field.get("name", "")).strip()
    value = str(field.get("value", "")).strip()
    if not name or not value:
        return {}

    normalized: dict[str, Any] = {
        "name": name,
        "value": value,
        "confidence": _to_confidence(field.get("confidence", 0.75)),
    }

    unit = field.get("unit")
    if unit is not None and str(unit).strip():
        normalized["unit"] = str(unit).strip()

    reference_range = field.get("referenceRange")
    if reference_range is not None and str(reference_range).strip():
        normalized["referenceRange"] = str(reference_range).strip()

    standard_code = field.get("standardCode")
    if standard_code is not None and str(standard_code).strip():
        normalized["standardCode"] = str(standard_code).strip()

    evidence_raw = field.get("evidence")
    if isinstance(evidence_raw, dict):
        evidence: dict[str, Any] = {
            "sourceFile": str(evidence_raw.get("sourceFile", object_key)),
            "snippet": str(evidence_raw.get("snippet", value[:120])),
        }
        page = _to_page(evidence_raw.get("page"))
        if page is not None:
            evidence["page"] = page
        normalized["evidence"] = evidence

    return normalized


def _average_confidence(fields: list[dict[str, Any]]) -> float:
    """计算字段的平均置信度。"""
    valid_scores = [
        _to_confidence(item.get("confidence", 0.0)) for item in fields if item
    ]
    if not valid_scores:
        return 0.0
    return round(sum(valid_scores) / len(valid_scores), 4)


def _to_confidence(value: Any) -> float:
    """将值转换为置信度浮点数。"""
    try:
        numeric = float(value)
    except (TypeError, ValueError):
        return 0.0
    if numeric < 0:
        return 0.0
    if numeric > 1:
        return 1.0
    return round(numeric, 4)


def _to_page(value: Any) -> int | None:
    """将值转换为页码。"""
    try:
        page = int(value)
    except (TypeError, ValueError):
        return None
    return page if page > 0 else None
