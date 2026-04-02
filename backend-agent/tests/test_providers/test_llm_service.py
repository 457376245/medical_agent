from __future__ import annotations

from typing import Any

import pytest

from app.providers.llm import LLMError, LLMService


class _StubStorage:
    def download_bytes(self, object_key: str) -> bytes:
        return f"content:{object_key}".encode("utf-8")


class _StubDocument:
    def __init__(self, parts: list[dict[str, Any]]) -> None:
        self._parts = parts

    def build_parse_content(
        self, file_type: str, object_key: str, content: bytes
    ) -> list[dict[str, Any]]:
        del file_type, object_key, content
        return list(self._parts)

    @staticmethod
    def contains_visual_parts(parts: list[dict[str, Any]]) -> bool:
        return any(item.get("type") == "image_url" for item in parts if isinstance(item, dict))


def test_model_for_attempt_uses_operation_specific_primary_and_fallback(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OPENAI_PARSE_MODEL", "parse-primary")
    monkeypatch.setenv("OPENAI_GENERATE_MODEL", "generate-primary")
    monkeypatch.setenv("OPENAI_FALLBACK_MODEL", "fallback-model")

    service = LLMService(storage=_StubStorage(), document=_StubDocument([]))

    assert service.model_for_attempt("parse", 1) == "parse-primary"
    assert service.model_for_attempt("generate", 1) == "generate-primary"
    assert service.model_for_attempt("parse", 2) == "fallback-model"
    assert service.model_for_attempt("generate", 3) == "fallback-model"


def test_parse_uses_vision_model_for_visual_parts(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("OPENAI_BASE_URL", "http://example.test")
    monkeypatch.setenv("OPENAI_API_KEY", "secret")
    monkeypatch.setenv("OPENAI_PARSE_MODEL", "parse-primary")
    monkeypatch.setenv("OPENAI_VISION_MODEL", "vision-primary")

    document = _StubDocument(
        [
            {"type": "text", "text": "prompt"},
            {"type": "image_url", "image_url": {"url": "data:image/png;base64,abc"}},
        ]
    )
    service = LLMService(storage=_StubStorage(), document=document)
    captured_payloads: list[dict[str, Any]] = []

    def fake_send_chat_completion_request(*, payload: dict[str, Any], attempt: int) -> tuple[int, dict[str, Any]]:
        del attempt
        captured_payloads.append(payload)
        return (
            200,
            {
                "choices": [
                    {
                        "message": {
                            "content": (
                                '{"fields":[{"name":"葡萄糖","value":"5.1","confidence":0.95}]}'
                            )
                        }
                    }
                ]
            },
        )

    monkeypatch.setattr(service, "_send_chat_completion_request", fake_send_chat_completion_request)
    result = service.parse(
        {"assetRefs": [{"objectKey": "scan.png", "fileType": "IMAGE"}]},
        "parse-primary",
        1,
    )

    assert captured_payloads[0]["model"] == "vision-primary"
    assert result["modelMeta"]["provider"] == "openai-compatible"
    assert result["structuredResult"]["fields"][0]["name"] == "葡萄糖"


def test_generate_returns_content_and_model_meta(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("OPENAI_BASE_URL", "http://example.test")
    monkeypatch.setenv("OPENAI_API_KEY", "secret")
    service = LLMService(storage=_StubStorage(), document=_StubDocument([]))

    def fake_send_chat_completion_request(*, payload: dict[str, Any], attempt: int) -> tuple[int, dict[str, Any]]:
        del payload, attempt
        return 200, {"choices": [{"message": {"content": "生成完成"}}]}

    monkeypatch.setattr(service, "_send_chat_completion_request", fake_send_chat_completion_request)
    result = service.generate({"type": "SUMMARY", "recordId": "r-1"}, "gpt-5.4-mini", 1)

    assert result["content"] == "生成完成"
    assert result["modelMeta"]["provider"] == "openai-compatible"
    assert result["modelMeta"]["model"] == "gpt-5.4-mini"


def test_parse_invalid_json_raises_biz_invalid_output(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OPENAI_BASE_URL", "http://example.test")
    monkeypatch.setenv("OPENAI_API_KEY", "secret")
    service = LLMService(
        storage=_StubStorage(),
        document=_StubDocument([{"type": "text", "text": "prompt"}]),
    )

    def fake_send_chat_completion_request(*, payload: dict[str, Any], attempt: int) -> tuple[int, dict[str, Any]]:
        del payload, attempt
        return 200, {"choices": [{"message": {"content": "not-json"}}]}

    monkeypatch.setattr(service, "_send_chat_completion_request", fake_send_chat_completion_request)

    with pytest.raises(LLMError) as exc_info:
        service.parse(
            {"assetRefs": [{"objectKey": "report.pdf", "fileType": "PDF"}]},
            "gpt-5.4",
            1,
        )

    assert exc_info.value.code == "BIZ_INVALID_LLM_OUTPUT"
