from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.providers.gateway import ProviderGateway
from app.providers.ocr_google import OCRError


@dataclass
class _StubLLM:
    sequence: list[Any]
    parse_calls: int = 0

    def model_for_attempt(self, attempt: int) -> str:
        return f"model-{attempt}"

    def parse(self, payload: dict[str, Any], model_name: str, attempt: int) -> dict[str, Any]:
        self.parse_calls += 1
        current = self.sequence[self.parse_calls - 1]
        if isinstance(current, Exception):
            raise current
        return current

    def generate(self, payload: dict[str, Any], model_name: str, attempt: int) -> dict[str, Any]:
        return {"content": "ok", "modelMeta": {"model": model_name}}


def test_gateway_retries_on_ext_ocr_error() -> None:
    llm = _StubLLM(
        sequence=[
            OCRError("temporary outage", code="EXT_OCR_UNAVAILABLE"),
            {
                "structuredResult": {"schemaVersion": "v1", "fields": [], "meta": {}},
                "confidence": 0.6,
                "modelMeta": {"model": "model-2"},
            },
        ]
    )
    gateway = ProviderGateway(llm=llm)
    gateway._sleep_before_retry = lambda _: None  # type: ignore[method-assign]

    result = gateway.execute_with_resilience("parse", {"assetRefs": [{"objectKey": "x"}]})

    assert result.success is True
    assert result.attempts == 2
    assert llm.parse_calls == 2


def test_gateway_does_not_retry_on_biz_ocr_error() -> None:
    llm = _StubLLM(sequence=[OCRError("credential missing", code="BIZ_OCR_NOT_CONFIGURED")])
    gateway = ProviderGateway(llm=llm)
    gateway._sleep_before_retry = lambda _: None  # type: ignore[method-assign]

    result = gateway.execute_with_resilience("parse", {"assetRefs": [{"objectKey": "x"}]})

    assert result.success is False
    assert result.error_code == "BIZ_OCR_NOT_CONFIGURED"
    assert result.attempts == 1
    assert llm.parse_calls == 1
