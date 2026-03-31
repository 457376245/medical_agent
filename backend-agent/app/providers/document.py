from __future__ import annotations

# pyright: reportMissingImports=false

import base64
import io
import json
import logging
import os
from typing import Any

import fitz  # type: ignore[import-not-found]
from pypdf import PdfReader  # type: ignore[import-not-found]

from app.providers.ocr_google import GoogleVisionOCRService, OCRError
from app.utils import read_int_env, to_bool


LOGGER = logging.getLogger(__name__)

MAX_PDF_TEXT_CHARS = 18_000


class DocumentParser:
    """Converts raw file bytes into LLM-ready content payloads."""

    def __init__(self, ocr: GoogleVisionOCRService | None = None) -> None:
        self._ocr = ocr or GoogleVisionOCRService()
        self._vision_fallback_to_gemini = to_bool(
            os.getenv("VISION_OCR_FALLBACK_TO_GEMINI", "true")
        )
        self._vision_pdf_max_pages = read_int_env("VISION_OCR_MAX_PAGES", 3, 1)

    def build_parse_content(
        self, file_type: str, object_key: str, content: bytes
    ) -> list[dict[str, Any]]:
        """Build a multimodal content list suitable for LangChain HumanMessage.

        After building the payload the caller should ``del content`` to free
        the raw bytes as early as possible.
        """
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
            f"Schema: {json.dumps(schema_hint, ensure_ascii=False)}. "
            f"Source file: {object_key}"
        )

        is_pdf = file_type == "PDF" or object_key.lower().endswith(".pdf")
        if is_pdf:
            text = self._extract_pdf_text(content)
            if text:
                self._log_ocr_path("pdf_text", object_key)
                return [
                    {
                        "type": "text",
                        "text": f"{prompt}\nDocument text:\n{text[:MAX_PDF_TEXT_CHARS]}",
                    }
                ]

            page_images = self._render_pdf_pages(content, max_pages=self._vision_pdf_max_pages)
            if not page_images:
                raise ValueError("BIZ_PDF_PARSE_FAILED")
            try:
                ocr_result = self._ocr.extract_text_from_pages(page_images, mime_type="image/png")
                self._log_ocr_path("vision", object_key)
                return [
                    {
                        "type": "text",
                        "text": (
                            f"{prompt}\nDocument text:\n"
                            f"{ocr_result.text[:MAX_PDF_TEXT_CHARS]}"
                        ),
                    }
                ]
            except OCRError as exc:
                LOGGER.warning(
                    "Vision OCR failed for PDF, object_key=%s code=%s fallback=%s",
                    object_key,
                    exc.code,
                    self._vision_fallback_to_gemini,
                )
                if not self._vision_fallback_to_gemini:
                    raise
                self._log_ocr_path("gemini_fallback", object_key)
                return self._build_gemini_image_parts(prompt, page_images, mime_type="image/png")

        mime_type = self._guess_image_mime(object_key)
        try:
            ocr_text = self._ocr.extract_text_from_image(content, mime_type=mime_type)
            self._log_ocr_path("vision", object_key)
            return [{"type": "text", "text": f"{prompt}\nDocument text:\n{ocr_text}"}]
        except OCRError as exc:
            LOGGER.warning(
                "Vision OCR failed for image, object_key=%s code=%s fallback=%s",
                object_key,
                exc.code,
                self._vision_fallback_to_gemini,
            )
            if not self._vision_fallback_to_gemini:
                raise
            self._log_ocr_path("gemini_fallback", object_key)
            return self._build_gemini_image_parts(prompt, [content], mime_type=mime_type)

    # ------------------------------------------------------------------
    # PDF helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _extract_pdf_text(content: bytes) -> str:
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
            LOGGER.warning("Failed to extract PDF text via pypdf", exc_info=True)
            return ""

    @staticmethod
    def _render_pdf_pages(content: bytes, *, max_pages: int) -> list[bytes]:
        images: list[bytes] = []
        try:
            with fitz.open(stream=content, filetype="pdf") as document:
                page_count = min(document.page_count, max_pages)
                for page_index in range(page_count):
                    page = document.load_page(page_index)
                    page_obj: Any = page
                    pix = page_obj.get_pixmap(alpha=False)
                    images.append(pix.tobytes("png"))
        except Exception:
            LOGGER.warning("Failed to render PDF pages via PyMuPDF", exc_info=True)
            return []
        return images

    # ------------------------------------------------------------------
    # MIME detection
    # ------------------------------------------------------------------

    @staticmethod
    def _guess_image_mime(object_key: str) -> str:
        lower = object_key.lower()
        if lower.endswith(".png"):
            return "image/png"
        if lower.endswith((".jpg", ".jpeg")):
            return "image/jpeg"
        if lower.endswith(".webp"):
            return "image/webp"
        if lower.endswith(".bmp"):
            return "image/bmp"
        if lower.endswith((".tif", ".tiff")):
            return "image/tiff"
        return "image/png"

    @staticmethod
    def _build_gemini_image_parts(
        prompt: str, images: list[bytes], *, mime_type: str
    ) -> list[dict[str, Any]]:
        parts: list[dict[str, Any]] = [{"type": "text", "text": prompt}]
        for image_bytes in images:
            image_b64 = base64.b64encode(image_bytes).decode("utf-8")
            parts.append(
                {
                    "type": "image_url",
                    "image_url": f"data:{mime_type};base64,{image_b64}",
                }
            )
        return parts

    @staticmethod
    def _log_ocr_path(path: str, object_key: str) -> None:
        LOGGER.info(
            "Document parse route selected: ocr_path=%s object_key=%s",
            path,
            object_key,
            extra={"ocr_path": path, "object_key": object_key},
        )
