from __future__ import annotations

# pyright: reportMissingImports=false

import base64
import io
import json
import logging
from typing import Any

import fitz  # type: ignore[import-not-found]
from pypdf import PdfReader  # type: ignore[import-not-found]


LOGGER = logging.getLogger(__name__)

MAX_PDF_TEXT_CHARS = 18_000


class DocumentParser:
    """Converts raw file bytes into LLM-ready content payloads."""

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
                return [
                    {
                        "type": "text",
                        "text": f"{prompt}\nDocument text:\n{text[:MAX_PDF_TEXT_CHARS]}",
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
    def _render_pdf_pages(content: bytes) -> list[str]:
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
