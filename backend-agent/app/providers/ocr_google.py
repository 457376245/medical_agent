from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from typing import Any

from app.utils import read_float_env, to_bool


LOGGER = logging.getLogger(__name__)


class OCRError(Exception):
    """Raised when OCR interaction fails."""

    def __init__(self, message: str, *, code: str) -> None:
        super().__init__(message)
        self.code = code


@dataclass
class OCRResult:
    text: str
    page_count: int


class GoogleVisionOCRService:
    """Google Vision OCR wrapper for image and rendered PDF pages."""

    def __init__(self) -> None:
        self._enabled = to_bool(os.getenv("VISION_OCR_ENABLED", "true"))
        self._credentials_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "").strip()
        raw_hints = os.getenv("VISION_OCR_LANGUAGE_HINTS", "zh-CN,en").strip()
        self._language_hints = [item.strip() for item in raw_hints.split(",") if item.strip()]
        self._timeout_seconds = read_float_env("VISION_OCR_TIMEOUT_SECONDS", 20.0, 1.0)
        self._client: Any | None = None

    @property
    def is_enabled(self) -> bool:
        return self._enabled

    def extract_text_from_image(self, image_bytes: bytes, *, mime_type: str) -> str:
        result = self.extract_text_from_pages([image_bytes], mime_type=mime_type)
        return result.text

    def extract_text_from_pages(
        self, page_images: list[bytes], *, mime_type: str = "image/png"
    ) -> OCRResult:
        self._ensure_configured()
        if not page_images:
            raise OCRError("No OCR page images were provided", code="BIZ_OCR_EMPTY_RESULT")

        client = self._get_client()
        texts: list[str] = []
        for index, image_bytes in enumerate(page_images, start=1):
            page_text = self._detect_text(
                client,
                image_bytes=image_bytes,
                mime_type=mime_type,
                page_no=index,
            )
            if page_text:
                texts.append(f"--- Page {index} ---\n{page_text}")

        merged = "\n\n".join(texts).strip()
        if not merged:
            raise OCRError("Google Vision returned empty OCR text", code="BIZ_OCR_EMPTY_RESULT")
        return OCRResult(text=merged, page_count=len(page_images))

    def _ensure_configured(self) -> None:
        if not self._enabled:
            raise OCRError("Vision OCR is disabled", code="BIZ_OCR_NOT_CONFIGURED")
        if not self._credentials_path:
            raise OCRError(
                "GOOGLE_APPLICATION_CREDENTIALS is not set",
                code="BIZ_OCR_NOT_CONFIGURED",
            )
        if not os.path.isfile(self._credentials_path):
            raise OCRError(
                "GOOGLE_APPLICATION_CREDENTIALS file does not exist",
                code="BIZ_OCR_NOT_CONFIGURED",
            )

    def _get_client(self) -> Any:
        if self._client is not None:
            return self._client
        try:
            from google.auth.exceptions import DefaultCredentialsError
            from google.cloud import vision
        except Exception as exc:
            raise OCRError(
                "google-cloud-vision package is not available",
                code="BIZ_OCR_NOT_CONFIGURED",
            ) from exc

        try:
            self._client = vision.ImageAnnotatorClient()
            return self._client
        except DefaultCredentialsError as exc:
            raise OCRError(
                "Google Vision credentials are invalid",
                code="BIZ_OCR_NOT_CONFIGURED",
            ) from exc
        except Exception as exc:
            raise OCRError(
                f"Failed to initialize Google Vision client: {exc}",
                code="EXT_OCR_UNAVAILABLE",
            ) from exc

    def _detect_text(
        self,
        client: Any,
        *,
        image_bytes: bytes,
        mime_type: str,
        page_no: int,
    ) -> str:
        try:
            from google.api_core.exceptions import GoogleAPICallError, RetryError
            from google.cloud import vision

            image = vision.Image(content=image_bytes)
            image_context = (
                vision.ImageContext(language_hints=self._language_hints)
                if self._language_hints
                else None
            )
            response = client.document_text_detection(
                image=image,
                image_context=image_context,
                timeout=self._timeout_seconds,
            )
            if response.error and response.error.message:
                raise OCRError(
                    f"Google Vision API error on page {page_no}: {response.error.message}",
                    code="EXT_OCR_UNAVAILABLE",
                )
            text = ""
            if response.full_text_annotation and response.full_text_annotation.text:
                text = response.full_text_annotation.text.strip()
            if not text:
                LOGGER.warning(
                    "Vision OCR page returned empty text: page=%s mime_type=%s",
                    page_no,
                    mime_type,
                )
            return text
        except OCRError:
            raise
        except (GoogleAPICallError, RetryError, TimeoutError) as exc:
            raise OCRError(
                f"Google Vision OCR request failed: {exc}",
                code="EXT_OCR_UNAVAILABLE",
            ) from exc
        except Exception as exc:
            raise OCRError(
                f"Unexpected Google Vision OCR failure: {exc}",
                code="EXT_OCR_UNAVAILABLE",
            ) from exc
