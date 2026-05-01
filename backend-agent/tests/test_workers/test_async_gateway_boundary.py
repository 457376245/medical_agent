from __future__ import annotations

import asyncio
from typing import Any

from app.providers.gateway import ProviderResponse
from app.workers.generate_worker import GenerateWorker
from app.workers.parse_worker import ParseWorker


class _AsyncGateway:
    def __init__(self, response: ProviderResponse) -> None:
        self.response = response
        self.calls: list[tuple[str, dict[str, Any]]] = []

    async def aexecute_with_resilience(
        self, operation: str, payload: dict[str, Any]
    ) -> ProviderResponse:
        self.calls.append((operation, payload))
        return self.response


def test_parse_worker_uses_gateway_async_facade() -> None:
    gateway = _AsyncGateway(
        ProviderResponse(
            success=True,
            payload={
                "structuredResult": {
                    "schemaVersion": "v1",
                    "fields": [{"name": "ALT", "value": "85", "confidence": 0.9}],
                    "meta": {},
                },
                "confidence": 0.9,
                "modelMeta": {"model": "parse-model"},
            },
            attempts=1,
        )
    )
    payload = {
        "jobId": "job-1",
        "sourceType": "LAB",
        "assetRefs": [{"objectKey": "reports/a.pdf", "fileType": "PDF"}],
    }

    result = asyncio.run(ParseWorker(gateway).handle(payload))  # type: ignore[arg-type]

    assert gateway.calls == [("parse", payload)]
    assert result["status"] == "SUCCESS"
    assert result["structuredResult"]["fields"][0]["name"] == "ALT"


def test_generate_worker_uses_gateway_async_facade() -> None:
    gateway = _AsyncGateway(
        ProviderResponse(
            success=True,
            payload={
                "type": "SUMMARY",
                "content": "复诊摘要",
                "modelMeta": {"model": "generate-model"},
            },
            attempts=1,
        )
    )
    payload = {"recordId": "record-1", "type": "SUMMARY"}

    result = asyncio.run(GenerateWorker(gateway).handle(payload))  # type: ignore[arg-type]

    assert gateway.calls == [("generate", payload)]
    assert result["status"] == "SUCCESS"
    assert result["content"] == "复诊摘要"
