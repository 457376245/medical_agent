from __future__ import annotations

# pyright: reportMissingImports=false

import json
import logging
import os
import warnings
from typing import Any

from langchain_core.messages import HumanMessage  # type: ignore[import-not-found]
from langchain_google_genai import ChatGoogleGenerativeAI  # type: ignore[import-not-found]
from pydantic import BaseModel, ConfigDict, Field

from app.providers.document import DocumentParser
from app.providers.storage import OSSStorageService
from app.utils import read_float_env, read_int_env, to_bool


LOGGER = logging.getLogger(__name__)

warnings.filterwarnings(
    "ignore",
    category=UserWarning,
    module=r"pydantic\.main",
    message=r"Pydantic serializer warnings:.*",
)


# ------------------------------------------------------------------
# Pydantic models for LLM structured output
# ------------------------------------------------------------------


class ParseEvidence(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    source_file: str = Field(alias="sourceFile")
    page: int | None = None
    snippet: str | None = None


class ParseField(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    name: str
    value: str
    unit: str | None = None
    reference_range: str | None = Field(default=None, alias="referenceRange")
    confidence: float = Field(ge=0, le=1)
    evidence: ParseEvidence | None = None


class ParseAgentOutput(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    fields: list[ParseField] = Field(default_factory=list)


class GenerateAgentOutput(BaseModel):
    content: str = Field(description="Generated draft content")


# ------------------------------------------------------------------
# Exception
# ------------------------------------------------------------------


class LLMError(Exception):
    """Raised when an LLM interaction fails."""

    def __init__(self, message: str, *, code: str) -> None:
        super().__init__(message)
        self.code = code


# ------------------------------------------------------------------
# Service
# ------------------------------------------------------------------


class LLMService:
    """Manages prompt construction and LLM invocations via LangChain."""

    def __init__(
        self,
        storage: OSSStorageService | None = None,
        document: DocumentParser | None = None,
    ) -> None:
        self._storage = storage or OSSStorageService()
        self._document = document or DocumentParser()

        # Gemini / LLM config
        self._google_api_key = os.getenv("GOOGLE_API_KEY", "").strip()
        self._gemini_model = os.getenv("GEMINI_MODEL", "gemini-2.5-pro").strip()
        self._gemini_fallback_model = os.getenv(
            "GEMINI_FALLBACK_MODEL", "gemini-2.5-flash"
        ).strip()
        self._gemini_temperature = float(os.getenv("GEMINI_TEMPERATURE", "0"))
        self._gemini_request_timeout_seconds = read_float_env(
            "GEMINI_REQUEST_TIMEOUT_SECONDS", 90.0, 1.0
        )
        self._gemini_sdk_retries = read_int_env("GEMINI_SDK_RETRIES", 2, 0)
        self._gemini_base_url = os.getenv("GEMINI_BASE_URL", "").strip()
        self._gemini_trust_env = to_bool(os.getenv("GEMINI_TRUST_ENV", "false"))
        self._gemini_proxy = os.getenv("GEMINI_PROXY", "").strip()
        self._gemini_retry_with_env_proxy = to_bool(
            os.getenv("GEMINI_RETRY_WITH_ENV_PROXY", "true")
        )
        self._gemini_http2 = to_bool(os.getenv("GEMINI_HTTP2", "false"))

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def model_for_attempt(self, attempt: int) -> str:
        if attempt == 1 or not self._gemini_fallback_model:
            return self._gemini_model
        return self._gemini_fallback_model

    def parse(
        self, payload: dict[str, Any], model_name: str, attempt: int
    ) -> dict[str, Any]:
        """Download a file from OSS, parse it via the LLM, return structured fields.

        Raises:
            OSSError: if the file cannot be downloaded.
            LLMError: if the LLM returns invalid or empty output.
            ValueError: for business-logic validation failures (BIZ_*).
        """
        asset_refs = payload.get("assetRefs")
        if not isinstance(asset_refs, list) or not asset_refs:
            raise ValueError("BIZ_MISSING_ASSET_REFS")

        first_asset = asset_refs[0] if isinstance(asset_refs[0], dict) else {}
        object_key = str(first_asset.get("objectKey", "")).strip().lstrip("/")
        file_type = str(first_asset.get("fileType", "")).upper()
        if not object_key:
            raise ValueError("BIZ_MISSING_OBJECT_KEY")

        # Download + build content, free raw bytes early
        content = self._storage.download_bytes(object_key)
        user_content = self._document.build_parse_content(
            file_type, object_key, content
        )
        del content  # free raw bytes before LLM call

        structured = self._invoke_structured(
            ParseAgentOutput, user_content, model_name, attempt
        )

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
                "LLM returned no usable fields", code="BIZ_EMPTY_PARSE_RESULT"
            )

        confidence = _average_confidence(fields)
        meta = {
            "provider": "langchain-google-genai",
            "framework": "langchain-v1",
            "model": model_name,
        }
        return {
            "structuredResult": {
                "schemaVersion": "v1",
                "fields": fields,
                "meta": meta,
            },
            "confidence": confidence,
            "modelMeta": meta,
        }

    def generate(
        self, payload: dict[str, Any], model_name: str, attempt: int
    ) -> dict[str, Any]:
        """Generate medical draft text via the LLM.

        Raises:
            LLMError: if the LLM returns invalid or empty output.
        """
        output_type = str(payload.get("type", "SUMMARY")).upper()
        system_prompt = (
            "You generate clinically cautious Chinese draft text. "
            "Never claim diagnosis certainty and avoid medication decisions."
        )
        if output_type == "MED_PLAN":
            task_prompt = (
                "Generate a medication plan draft in Chinese, with clear steps, "
                "missing information reminders, and a reconfirmation warning."
            )
        elif output_type == "REPORT_ANALYSIS":
            task_prompt = (
                "You will receive structured lab/report fields. "
                "Generate Chinese analysis and advice in at most 300 Chinese characters. "
                "Focus on abnormalities, possible risk direction, and practical follow-up suggestions. "
                "Do not provide definitive diagnosis or medication decisions. "
                "Must include a short disclaimer that this is for reference only."
            )
        else:
            task_prompt = (
                "Generate a concise medical report summary draft in Chinese. "
                "Highlight key findings and explicitly mention unknown fields."
            )

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
        prompt = f"{system_prompt}\n{user_prompt}"

        structured = self._invoke_structured(
            GenerateAgentOutput,
            [{"type": "text", "text": prompt}],
            model_name,
            attempt,
        )

        text = structured.content.strip()
        if not text:
            raise LLMError("LLM returned empty content", code="BIZ_EMPTY_GENERATION")

        return {
            "type": output_type,
            "content": text,
            "modelMeta": {
                "provider": "langchain-google-genai",
                "framework": "langchain-v1",
                "model": model_name,
            },
        }

    # ------------------------------------------------------------------
    # Internals
    # ------------------------------------------------------------------

    def _invoke_structured(
        self,
        schema: type[BaseModel],
        user_content: list[dict[str, Any]],
        model_name: str,
        attempt: int,
    ) -> Any:
        """Invoke the LLM with structured output and coerce the result."""
        llm = self._chat_model(model_name, attempt).with_structured_output(
            schema, method="json_schema"
        )
        with warnings.catch_warnings():
            warnings.filterwarnings(
                "ignore",
                category=UserWarning,
                message=r"Pydantic serializer warnings:.*PydanticSerializationUnexpectedValue.*",
            )
            result = llm.invoke([HumanMessage(content=user_content)])

        if isinstance(result, schema):
            return result
        if isinstance(result, BaseModel):
            return schema.model_validate(result.model_dump())
        if isinstance(result, dict):
            return schema.model_validate(result)
        raise LLMError(
            f"Unexpected LLM output type: {type(result).__name__}",
            code="BIZ_INVALID_LLM_OUTPUT",
        )

    def _chat_model(self, model_name: str, attempt: int) -> ChatGoogleGenerativeAI:
        api_key = (
            self._google_api_key
            or os.getenv("GOOGLE_API_KEY", "").strip()
            or os.getenv("GEMINI_API_KEY", "").strip()
        )
        if not api_key:
            raise LLMError(
                "Gemini API key not configured", code="BIZ_GEMINI_NOT_CONFIGURED"
            )
        trust_env = self._gemini_trust_env
        if (
            self._gemini_retry_with_env_proxy
            and not self._gemini_proxy
            and attempt >= 2
        ):
            trust_env = (
                not self._gemini_trust_env
                if attempt % 2 == 0
                else self._gemini_trust_env
            )

        client_args: dict[str, Any] = {
            "trust_env": trust_env,
            "http2": self._gemini_http2,
        }
        if self._gemini_proxy:
            client_args["proxy"] = self._gemini_proxy
        model_kwargs: dict[str, Any] = {
            "model": model_name,
            "google_api_key": api_key,
            "temperature": self._gemini_temperature,
            "max_retries": self._gemini_sdk_retries,
            "timeout": self._gemini_request_timeout_seconds,
            "client_args": client_args,
        }
        if self._gemini_base_url:
            model_kwargs["base_url"] = self._gemini_base_url
        return ChatGoogleGenerativeAI(**model_kwargs)


# ------------------------------------------------------------------
# Pure helper functions (no state, previously inside ProviderGateway)
# ------------------------------------------------------------------


def _normalize_field(field: dict[str, Any], object_key: str) -> dict[str, Any]:
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
    valid_scores = [
        _to_confidence(item.get("confidence", 0.0)) for item in fields if item
    ]
    if not valid_scores:
        return 0.0
    return round(sum(valid_scores) / len(valid_scores), 4)


def _to_confidence(value: Any) -> float:
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
    try:
        page = int(value)
    except (TypeError, ValueError):
        return None
    return page if page > 0 else None
