from __future__ import annotations

from dataclasses import dataclass

import pytest

from app.providers.document import DocumentParser
from app.providers.ocr_google import OCRError, OCRResult


@dataclass
class _StubOCR:
    image_text: str = ""
    pages_text: str = ""
    image_error: OCRError | None = None
    pages_error: OCRError | None = None
    image_calls: int = 0
    pages_calls: int = 0

    def extract_text_from_image(self, image_bytes: bytes, *, mime_type: str) -> str:
        self.image_calls += 1
        if self.image_error:
            raise self.image_error
        return self.image_text

    def extract_text_from_pages(
        self, page_images: list[bytes], *, mime_type: str = "image/png"
    ) -> OCRResult:
        self.pages_calls += 1
        if self.pages_error:
            raise self.pages_error
        return OCRResult(text=self.pages_text, page_count=len(page_images))


def test_pdf_with_text_prefers_pdf_text(monkeypatch: pytest.MonkeyPatch) -> None:
    ocr = _StubOCR(pages_text="vision text")
    parser = DocumentParser(ocr=ocr)
    monkeypatch.setattr(
        DocumentParser,
        "_extract_pdf_text",
        staticmethod(lambda _: "text from pypdf"),
    )

    result = parser.build_parse_content("PDF", "report.pdf", b"dummy")

    assert len(result) == 1
    assert result[0]["type"] == "text"
    assert "text from pypdf" in result[0]["text"]
    assert ocr.pages_calls == 0


def test_pdf_without_text_uses_vision(monkeypatch: pytest.MonkeyPatch) -> None:
    ocr = _StubOCR(pages_text="--- Page 1 ---\nvision text")
    parser = DocumentParser(ocr=ocr)
    monkeypatch.setattr(DocumentParser, "_extract_pdf_text", staticmethod(lambda _: ""))
    monkeypatch.setattr(
        DocumentParser,
        "_render_pdf_pages",
        staticmethod(lambda _content, *, max_pages: [b"page-1"][:max_pages]),
    )

    result = parser.build_parse_content("PDF", "scan.pdf", b"dummy")

    assert len(result) == 1
    assert result[0]["type"] == "text"
    assert "vision text" in result[0]["text"]
    assert ocr.pages_calls == 1


def test_image_uses_vision_text() -> None:
    ocr = _StubOCR(image_text="vision image text")
    parser = DocumentParser(ocr=ocr)

    result = parser.build_parse_content("IMAGE", "scan.png", b"image-content")

    assert len(result) == 1
    assert result[0]["type"] == "text"
    assert "vision image text" in result[0]["text"]
    assert ocr.image_calls == 1


def test_vision_error_falls_back_to_gemini(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("VISION_OCR_FALLBACK_TO_GEMINI", "true")
    ocr = _StubOCR(image_error=OCRError("not configured", code="BIZ_OCR_NOT_CONFIGURED"))
    parser = DocumentParser(ocr=ocr)

    result = parser.build_parse_content("IMAGE", "scan.png", b"image-content")

    part_types = [item["type"] for item in result]
    assert "text" in part_types
    assert "image_url" in part_types


def test_vision_error_without_fallback_raises(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("VISION_OCR_FALLBACK_TO_GEMINI", "false")
    ocr = _StubOCR(image_error=OCRError("vision down", code="EXT_OCR_UNAVAILABLE"))
    parser = DocumentParser(ocr=ocr)

    with pytest.raises(OCRError) as exc_info:
        parser.build_parse_content("IMAGE", "scan.png", b"image-content")

    assert exc_info.value.code == "EXT_OCR_UNAVAILABLE"
