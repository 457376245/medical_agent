from __future__ import annotations
# pyright: reportMissingImports=false

from dataclasses import dataclass
import base64
import io
import json
import logging
import os
import re
import time
from typing import Any, TypeVar, cast

import fitz  # type: ignore[import-not-found]
import oss2  # type: ignore[import-not-found]
from langchain.agents import create_agent  # type: ignore[import-not-found]
from langchain_google_genai import ChatGoogleGenerativeAI  # type: ignore[import-not-found]
from pydantic import BaseModel, ConfigDict, Field
from pypdf import PdfReader  # type: ignore[import-not-found]


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


StructuredModel = TypeVar("StructuredModel", bound=BaseModel)
LOGGER = logging.getLogger(__name__)


@dataclass
class ProviderResponse:
    success: bool
    payload: dict[str, Any]
    error_code: str | None = None
    attempts: int = 1


class ProviderGateway:
    def __init__(self) -> None:
        self._google_api_key = os.getenv("GOOGLE_API_KEY", "").strip()
        self._gemini_model = os.getenv("GEMINI_MODEL", "gemini-2.5-pro").strip()
        self._gemini_fallback_model = os.getenv(
            "GEMINI_FALLBACK_MODEL", "gemini-2.5-flash"
        ).strip()
        self._gemini_temperature = float(os.getenv("GEMINI_TEMPERATURE", "0"))

        endpoint = os.getenv("OSS_ENDPOINT", os.getenv("S3_ENDPOINT", "")).strip()
        if endpoint and not endpoint.startswith(("http://", "https://")):
            endpoint = f"https://{endpoint}"
        self._oss_endpoint = endpoint
        self._oss_bucket = os.getenv("OSS_BUCKET", "").strip()
        self._oss_access_key_id = os.getenv(
            "OSS_ACCESS_KEY_ID", os.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID", "")
        ).strip()
        self._oss_access_key_secret = os.getenv(
            "OSS_ACCESS_KEY_SECRET", os.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "")
        ).strip()

    def execute_with_resilience(
        self, operation: str, payload: dict[str, Any]
    ) -> ProviderResponse:
        max_attempts = 3
        backoff_seconds = 0.5
        for attempt in range(1, max_attempts + 1):
            try:
                simulation = str(payload.get("simulate", ""))
                if simulation == "timeout":
                    raise TimeoutError("Provider timeout")
                if simulation == "external_error":
                    raise ConnectionError("External provider unavailable")
                if simulation == "biz_error":
                    raise ValueError("BIZ_INVALID_INPUT")

                model_name = self._model_for_attempt(attempt)
                if operation == "parse":
                    LOGGER.info(
                        "Provider call started: operation=%s attempt=%s",
                        operation,
                        attempt,
                    )
                    parsed = self._parse_with_langchain(payload, model_name)
                    LOGGER.info(
                        "Provider call succeeded: operation=%s attempt=%s",
                        operation,
                        attempt,
                    )
                    return ProviderResponse(
                        success=True, payload=parsed, attempts=attempt
                    )
                if operation == "generate":
                    LOGGER.info(
                        "Provider call started: operation=%s attempt=%s",
                        operation,
                        attempt,
                    )
                    generated = self._generate_with_langchain(payload, model_name)
                    LOGGER.info(
                        "Provider call succeeded: operation=%s attempt=%s",
                        operation,
                        attempt,
                    )
                    return ProviderResponse(
                        success=True, payload=generated, attempts=attempt
                    )
                raise ValueError("BIZ_UNSUPPORTED_OPERATION")
            except ValueError as exc:
                LOGGER.warning(
                    "Provider business error: operation=%s attempt=%s code=%s message=%s",
                    operation,
                    attempt,
                    self._extract_biz_code(str(exc)),
                    str(exc),
                )
                return ProviderResponse(
                    success=False,
                    payload={"operation": operation, "input": payload},
                    error_code=self._extract_biz_code(str(exc)),
                    attempts=attempt,
                )
            except TimeoutError as exc:
                LOGGER.warning(
                    "Provider timeout: operation=%s attempt=%s message=%s",
                    operation,
                    attempt,
                    str(exc),
                )
                if attempt >= max_attempts:
                    return ProviderResponse(
                        success=False,
                        payload={"operation": operation, "input": payload},
                        error_code="EXT_TIMEOUT",
                        attempts=attempt,
                    )
                time.sleep(backoff_seconds * attempt)
            except (ConnectionError, oss2.exceptions.OssError) as exc:
                LOGGER.warning(
                    "Provider connectivity error: operation=%s attempt=%s error=%s",
                    operation,
                    attempt,
                    str(exc),
                )
                if attempt >= max_attempts:
                    return ProviderResponse(
                        success=False,
                        payload={"operation": operation, "input": payload},
                        error_code=(
                            "EXT_TIMEOUT"
                            if self._is_timeout_error(exc)
                            else "EXT_PROVIDER_UNAVAILABLE"
                        ),
                        attempts=attempt,
                    )
                time.sleep(backoff_seconds * attempt)
            except Exception as exc:  # pragma: no cover - defensive fallback
                LOGGER.exception(
                    "Provider unexpected error: operation=%s attempt=%s",
                    operation,
                    attempt,
                )
                if attempt >= max_attempts:
                    return ProviderResponse(
                        success=False,
                        payload={"operation": operation, "input": payload},
                        error_code=(
                            "EXT_TIMEOUT"
                            if self._is_timeout_error(exc)
                            else "EXT_PROVIDER_UNAVAILABLE"
                        ),
                        attempts=attempt,
                    )
                time.sleep(backoff_seconds * attempt)

        return ProviderResponse(
            success=False,
            payload={"operation": operation, "input": payload},
            error_code="EXT_UNKNOWN",
            attempts=max_attempts,
        )

    def _parse_with_langchain(
        self, payload: dict[str, Any], model_name: str
    ) -> dict[str, Any]:
        asset_refs = payload.get("assetRefs")
        if not isinstance(asset_refs, list) or not asset_refs:
            raise ValueError("BIZ_MISSING_ASSET_REFS")

        first_asset = asset_refs[0] if isinstance(asset_refs[0], dict) else {}
        object_key = str(first_asset.get("objectKey", "")).strip().lstrip("/")
        file_type = str(first_asset.get("fileType", "")).upper()
        if not object_key:
            raise ValueError("BIZ_MISSING_OBJECT_KEY")

        content = self._download_object_bytes(object_key)
        user_content = self._build_parse_content(file_type, object_key, content)

        parse_agent = create_agent(
            model=self._chat_model(model_name),
            tools=[],
            system_prompt=(
                "You are a medical report extraction agent. "
                "Return strictly structured fields with source evidence."
            ),
            response_format=ParseAgentOutput,
        )
        result = parse_agent.invoke(
            {
                "messages": [
                    {
                        "role": "user",
                        "content": user_content,
                    }
                ]
            }
        )
        structured = cast(
            ParseAgentOutput,
            self._coerce_structured_response(result, ParseAgentOutput),
        )
        normalized_fields = [
            self._normalize_field(
                field.model_dump(by_alias=True, exclude_none=True),
                object_key,
            )
            for field in structured.fields
        ]
        fields = [item for item in normalized_fields if item]
        if not fields:
            raise ValueError("BIZ_EMPTY_PARSE_RESULT")

        confidence = self._average_confidence(fields)
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

    def _generate_with_langchain(
        self, payload: dict[str, Any], model_name: str
    ) -> dict[str, Any]:
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
            "analysisContext": analysis_context if output_type == "REPORT_ANALYSIS" else {},
        }
        user_prompt = (
            f"{task_prompt}\n"
            "Context (JSON): "
            f"{json.dumps(safe_context, ensure_ascii=True)}"
        )
        generate_agent = create_agent(
            model=self._chat_model(model_name),
            tools=[],
            system_prompt=system_prompt,
            response_format=GenerateAgentOutput,
        )
        result = generate_agent.invoke(
            {"messages": [{"role": "user", "content": user_prompt}]}
        )
        structured = cast(
            GenerateAgentOutput,
            self._coerce_structured_response(result, GenerateAgentOutput),
        )
        content = structured.content.strip()
        if not content:
            raise ValueError("BIZ_EMPTY_GENERATION")

        return {
            "type": output_type,
            "content": content,
            "modelMeta": {
                "provider": "langchain-google-genai",
                "framework": "langchain-v1",
                "model": model_name,
            },
        }

    def _chat_model(self, model_name: str) -> ChatGoogleGenerativeAI:
        if self._google_api_key and not os.getenv("GOOGLE_API_KEY"):
            os.environ["GOOGLE_API_KEY"] = self._google_api_key
        if not os.getenv("GOOGLE_API_KEY"):
            raise ValueError("BIZ_GEMINI_NOT_CONFIGURED")
        return ChatGoogleGenerativeAI(
            model=model_name,
            temperature=self._gemini_temperature,
        )

    def _build_parse_content(
        self, file_type: str, object_key: str, content: bytes
    ) -> list[dict[str, Any]]:
        schema_hint = {
            "fields": [
                {
                    "name": "test_name",
                    "value": "test_value",
                    "unit": "optional",
                    "referenceRange": "optional",
                    "confidence": 0.85,
                    "evidence": {
                        "sourceFile": object_key,
                        "page": 1,
                        "snippet": "quoted evidence text",
                    },
                }
            ]
        }
        prompt = (
            "Extract key medical test fields from the report and respond in JSON. "
            "Keep confidence in [0,1]. "
            f"Schema: {json.dumps(schema_hint, ensure_ascii=True)}. "
            f"Source file: {object_key}"
        )

        is_pdf = file_type == "PDF" or object_key.lower().endswith(".pdf")
        if is_pdf:
            text = self._extract_pdf_text(content)
            if text:
                return [
                    {
                        "type": "text",
                        "text": f"{prompt}\nDocument text:\n{text[:18000]}",
                    }
                ]

            page_images = self._render_pdf_pages(content)
            if not page_images:
                raise ValueError("BIZ_PDF_PARSE_FAILED")
            return [{"type": "text", "text": prompt}] + [
                {
                    "type": "image_url",
                    "image_url": f"data:image/png;base64,{page_base64}",
                }
                for page_base64 in page_images
            ]

        mime_type = self._guess_image_mime(object_key)
        image_b64 = base64.b64encode(content).decode("utf-8")
        return [
            {"type": "text", "text": prompt},
            {
                "type": "image_url",
                "image_url": f"data:{mime_type};base64,{image_b64}",
            },
        ]

    def _download_object_bytes(self, object_key: str) -> bytes:
        if (
            not self._oss_endpoint
            or not self._oss_bucket
            or not self._oss_access_key_id
            or not self._oss_access_key_secret
        ):
            raise ValueError("BIZ_OSS_NOT_CONFIGURED")

        auth = oss2.Auth(self._oss_access_key_id, self._oss_access_key_secret)
        bucket = oss2.Bucket(auth, self._oss_endpoint, self._oss_bucket)
        response = bucket.get_object(object_key)
        data = response.read()
        if not data:
            raise ValueError("BIZ_EMPTY_UPLOAD_FILE")
        return data

    def _extract_pdf_text(self, content: bytes) -> str:
        try:
            with io.BytesIO(content) as stream:
                reader = PdfReader(stream)
                chunks: list[str] = []
                for page in reader.pages[:20]:
                    text = (page.extract_text() or "").strip()
                    if text:
                        chunks.append(text)
                return "\n".join(chunks).strip()
        except Exception:
            return ""

    def _render_pdf_pages(self, content: bytes) -> list[str]:
        images: list[str] = []
        try:
            with fitz.open(stream=content, filetype="pdf") as document:
                page_count = min(document.page_count, 3)
                for page_index in range(page_count):
                    page = document.load_page(page_index)
                    page_obj: Any = page
                    pix = page_obj.get_pixmap(alpha=False)
                    images.append(base64.b64encode(pix.tobytes("png")).decode("utf-8"))
        except Exception:
            return []
        return images

    def _guess_image_mime(self, object_key: str) -> str:
        lower = object_key.lower()
        if lower.endswith(".png"):
            return "image/png"
        if lower.endswith(".jpg") or lower.endswith(".jpeg"):
            return "image/jpeg"
        if lower.endswith(".webp"):
            return "image/webp"
        if lower.endswith(".bmp"):
            return "image/bmp"
        if lower.endswith(".tif") or lower.endswith(".tiff"):
            return "image/tiff"
        return "image/png"

    def _normalize_field(
        self, field: dict[str, Any], object_key: str
    ) -> dict[str, Any]:
        name = str(field.get("name", "")).strip()
        value = str(field.get("value", "")).strip()
        if not name or not value:
            return {}

        normalized: dict[str, Any] = {
            "name": name,
            "value": value,
            "confidence": self._to_confidence(field.get("confidence", 0.75)),
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
            page = self._to_page(evidence_raw.get("page"))
            if page is not None:
                evidence["page"] = page
            normalized["evidence"] = evidence

        return normalized

    def _coerce_structured_response(
        self, result: dict[str, Any], schema: type[StructuredModel]
    ) -> StructuredModel:
        structured = result.get("structured_response")
        if structured is None:
            raise ValueError("BIZ_INVALID_LLM_OUTPUT")
        if isinstance(structured, schema):
            return structured
        if isinstance(structured, BaseModel):
            return cast(StructuredModel, schema.model_validate(structured.model_dump()))
        if isinstance(structured, dict):
            return cast(StructuredModel, schema.model_validate(structured))
        raise ValueError("BIZ_INVALID_LLM_OUTPUT")

    def _average_confidence(self, fields: list[dict[str, Any]]) -> float:
        valid_scores = [
            self._to_confidence(item.get("confidence", 0.0)) for item in fields if item
        ]
        if not valid_scores:
            return 0.0
        return round(sum(valid_scores) / len(valid_scores), 4)

    def _to_confidence(self, value: Any) -> float:
        try:
            numeric = float(value)
        except (TypeError, ValueError):
            return 0.0
        if numeric < 0:
            return 0.0
        if numeric > 1:
            return 1.0
        return round(numeric, 4)

    def _to_page(self, value: Any) -> int | None:
        try:
            page = int(value)
        except (TypeError, ValueError):
            return None
        return page if page > 0 else None

    def _model_for_attempt(self, attempt: int) -> str:
        if attempt == 1 or not self._gemini_fallback_model:
            return self._gemini_model
        return self._gemini_fallback_model

    def _is_timeout_error(self, exc: Exception) -> bool:
        text = str(exc).lower()
        return "timeout" in text or "deadline" in text

    def _extract_biz_code(self, message: str) -> str:
        match = re.search(r"BIZ_[A-Z0-9_]+", message)
        if match:
            return match.group(0)
        return "BIZ_PROVIDER_FAILED"
