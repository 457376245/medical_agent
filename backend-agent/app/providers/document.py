from __future__ import annotations

# pyright: reportMissingImports=false

import base64
import io
import json
import logging
from typing import Any

import fitz  # type: ignore[import-not-found]
from pypdf import PdfReader  # type: ignore[import-not-found]

from app.utils import read_int_env


LOGGER = logging.getLogger(__name__)

MAX_PDF_TEXT_CHARS = 18_000


class DocumentParser:
    """将文件字节转换为 OpenAI 兼容的多模态内容。"""

    def __init__(self) -> None:
        self._vision_pdf_max_pages = read_int_env("VISION_OCR_MAX_PAGES", 3, 1)

    def build_parse_content(
        self, file_type: str, object_key: str, content: bytes
    ) -> list[dict[str, Any]]:
        """构建适用于 chat completions 的多模态内容列表。"""
        schema_hint = {
            "reportDate": "2024-01-15",
            "fields": [
                {
                    "name": "test_name",
                    "value": "test_value",
                    "unit": "optional",
                    "referenceRange": "optional",
                    "standardCode": "optional, e.g. ALT, AST, GLU",
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
            "Also extract reportDate in YYYY-MM-DD format if visible on the report. "
            "Keep confidence in [0,1]. "
            f"Schema: {json.dumps(schema_hint, ensure_ascii=False)}. "
            f"Source file: {object_key}"
        )

        is_pdf = file_type == "PDF" or object_key.lower().endswith(".pdf")
        if is_pdf:
            text = self._extract_pdf_text(content)
            if text:
                self._log_parse_route("pdf_text", object_key)
                return [
                    {
                        "type": "text",
                        "text": f"{prompt}\nDocument text:\n{text[:MAX_PDF_TEXT_CHARS]}",
                    }
                ]

            page_images = self._render_pdf_pages(content, max_pages=self._vision_pdf_max_pages)
            if not page_images:
                raise ValueError("BIZ_PDF_PARSE_FAILED")
            self._log_parse_route("openai_vision", object_key)
            return self._build_openai_image_parts(
                prompt,
                page_images,
                mime_type="image/png",
            )

        mime_type = self._guess_image_mime(object_key)
        self._log_parse_route("openai_vision", object_key)
        return self._build_openai_image_parts(prompt, [content], mime_type=mime_type)

    @staticmethod
    def contains_visual_parts(parts: list[dict[str, Any]]) -> bool:
        """检查内容是否包含图像部分。"""
        return any(
            isinstance(item, dict) and str(item.get("type", "")).strip() == "image_url"
            for item in parts
        )

    # ------------------------------------------------------------------
    # PDF 辅助方法
    # ------------------------------------------------------------------

    @staticmethod
    def _extract_pdf_text(content: bytes) -> str:
        """使用 pypdf 提取 PDF 文本。"""
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
            LOGGER.warning("通过 pypdf 提取 PDF 文本失败", exc_info=True)
            return ""

    @staticmethod
    def _render_pdf_pages(content: bytes, *, max_pages: int) -> list[bytes]:
        """使用 PyMuPDF 将 PDF 页面渲染为图像。"""
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
            LOGGER.warning("通过 PyMuPDF 渲染 PDF 页面失败", exc_info=True)
            return []
        return images

    # ------------------------------------------------------------------
    # MIME 类型检测
    # ------------------------------------------------------------------

    @staticmethod
    def _guess_image_mime(object_key: str) -> str:
        """根据文件扩展名推断 MIME 类型。"""
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
    def _build_openai_image_parts(
        prompt: str, images: list[bytes], *, mime_type: str
    ) -> list[dict[str, Any]]:
        """构建 OpenAI 图像内容部分。"""
        parts: list[dict[str, Any]] = [{"type": "text", "text": prompt}]
        for image_bytes in images:
            image_b64 = base64.b64encode(image_bytes).decode("utf-8")
            parts.append(
                {
                    "type": "image_url",
                    "image_url": {
                        "url": f"data:{mime_type};base64,{image_b64}",
                    },
                }
            )
        return parts

    @staticmethod
    def _log_parse_route(path: str, object_key: str) -> None:
        """记录解析路由选择。"""
        LOGGER.info(
            "Document parse route selected: path=%s object_key=%s",
            path,
            object_key,
            extra={"ocr_path": path, "object_key": object_key},
        )