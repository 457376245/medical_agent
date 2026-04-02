from __future__ import annotations

import base64

import pytest

from app.providers.document import DocumentParser


def test_pdf_with_text_prefers_pdf_text(monkeypatch: pytest.MonkeyPatch) -> None:
    parser = DocumentParser()
    monkeypatch.setattr(
        DocumentParser,
        "_extract_pdf_text",
        staticmethod(lambda _: "text from pypdf"),
    )

    result = parser.build_parse_content("PDF", "report.pdf", b"dummy")

    assert len(result) == 1
    assert result[0]["type"] == "text"
    assert "text from pypdf" in result[0]["text"]


def test_pdf_without_text_builds_openai_vision_parts(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    parser = DocumentParser()
    monkeypatch.setattr(DocumentParser, "_extract_pdf_text", staticmethod(lambda _: ""))
    monkeypatch.setattr(
        DocumentParser,
        "_render_pdf_pages",
        staticmethod(lambda _content, *, max_pages: [b"page-1", b"page-2"][:max_pages]),
    )

    result = parser.build_parse_content("PDF", "scan.pdf", b"dummy")

    assert result[0]["type"] == "text"
    assert result[1]["type"] == "image_url"
    assert result[2]["type"] == "image_url"
    assert DocumentParser.contains_visual_parts(result) is True
    assert result[1]["image_url"]["url"].startswith("data:image/png;base64,")


def test_image_builds_openai_vision_parts() -> None:
    parser = DocumentParser()

    result = parser.build_parse_content("IMAGE", "scan.png", b"image-content")

    assert len(result) == 2
    assert result[0]["type"] == "text"
    assert result[1]["type"] == "image_url"
    assert DocumentParser.contains_visual_parts(result) is True
    image_url = result[1]["image_url"]["url"]
    assert image_url == "data:image/png;base64," + base64.b64encode(b"image-content").decode(
        "utf-8"
    )


def test_pdf_without_text_and_pages_raises(monkeypatch: pytest.MonkeyPatch) -> None:
    parser = DocumentParser()
    monkeypatch.setattr(DocumentParser, "_extract_pdf_text", staticmethod(lambda _: ""))
    monkeypatch.setattr(
        DocumentParser,
        "_render_pdf_pages",
        staticmethod(lambda _content, *, max_pages: []),
    )

    with pytest.raises(ValueError) as exc_info:
        parser.build_parse_content("PDF", "broken.pdf", b"dummy")

    assert str(exc_info.value) == "BIZ_PDF_PARSE_FAILED"
