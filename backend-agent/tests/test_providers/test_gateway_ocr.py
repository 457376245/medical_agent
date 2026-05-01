from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any

from app.providers.gateway import ProviderGateway
from app.providers.llm import LLMError


@dataclass
class _StubLLM:
    sequence: list[Any]
    parse_calls: int = 0

    def model_for_attempt(self, operation: str, attempt: int) -> str:
        return f"{operation}-model-{attempt}"

    def parse(self, payload: dict[str, Any], model_name: str, attempt: int) -> dict[str, Any]:
        self.parse_calls += 1
        current = self.sequence[self.parse_calls - 1]
        if isinstance(current, Exception):
            raise current
        return current

    def generate(self, payload: dict[str, Any], model_name: str, attempt: int) -> dict[str, Any]:
        return {"content": "ok", "modelMeta": {"model": model_name}}


def test_gateway_retries_on_retryable_llm_error() -> None:
    llm = _StubLLM(
        sequence=[
            LLMError("temporary outage", code="EXT_PROVIDER_UNAVAILABLE"),
            {
                "structuredResult": {
                    "schemaVersion": "v1",
                    "fields": [{"name": "葡萄糖", "value": "5.1", "confidence": 0.9}],
                    "meta": {},
                },
                "confidence": 0.9,
                "modelMeta": {"model": "parse-model-2"},
            },
        ]
    )
    gateway = ProviderGateway(llm=llm)
    gateway._sleep_before_retry = lambda _: None  # type: ignore[method-assign]

    result = gateway.execute_with_resilience("parse", {"assetRefs": [{"objectKey": "x"}]})

    assert result.success is True
    assert result.attempts == 2
    assert llm.parse_calls == 2


def test_gateway_does_not_retry_on_biz_llm_error() -> None:
    llm = _StubLLM(
        sequence=[LLMError("request invalid", code="BIZ_LLM_REQUEST_INVALID")]
    )
    gateway = ProviderGateway(llm=llm)
    gateway._sleep_before_retry = lambda _: None  # type: ignore[method-assign]

    result = gateway.execute_with_resilience("parse", {"assetRefs": [{"objectKey": "x"}]})

    assert result.success is False
    assert result.error_code == "BIZ_LLM_REQUEST_INVALID"
    assert result.attempts == 1
    assert llm.parse_calls == 1


def test_gateway_async_facade_returns_sync_result() -> None:
    llm = _StubLLM(
        sequence=[
            {
                "structuredResult": {
                    "schemaVersion": "v1",
                    "fields": [{"name": "葡萄糖", "value": "5.1", "confidence": 0.9}],
                    "meta": {},
                },
                "confidence": 0.9,
                "modelMeta": {"model": "parse-model-1"},
            },
        ]
    )
    gateway = ProviderGateway(llm=llm)

    result = asyncio.run(
        gateway.aexecute_with_resilience("parse", {"assetRefs": [{"objectKey": "x"}]})
    )

    assert result.success is True
    assert result.attempts == 1
    assert llm.parse_calls == 1
