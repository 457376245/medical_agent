from __future__ import annotations

import asyncio
import logging
from typing import Any

from app.providers.gateway import ProviderGateway


LOGGER = logging.getLogger(__name__)


class ParseWorker:
    def __init__(self, gateway: ProviderGateway | None = None) -> None:
        self.gateway = gateway or ProviderGateway()

    async def handle(self, payload: dict[str, Any]) -> dict[str, Any]:
        LOGGER.info(
            "Parse task received: jobId=%s asset_ref_count=%s",
            payload.get("jobId"),
            len(payload.get("assetRefs", []))
            if isinstance(payload.get("assetRefs"), list)
            else 0,
        )
        if not payload.get("assetRefs"):
            LOGGER.warning("Parse task rejected: missing assetRefs")
            return {
                "status": "FAILED",
                "structuredResult": {},
                "confidence": 0.0,
                "errors": [
                    {
                        "code": "BIZ_MISSING_ASSET_REFS",
                        "message": "assetRefs are required",
                    }
                ],
            }
        result = await asyncio.to_thread(
            self.gateway.execute_with_resilience,
            "parse",
            payload,
        )
        if not result.success:
            LOGGER.error(
                "Parse provider failed: jobId=%s error_code=%s attempts=%s",
                payload.get("jobId"),
                result.error_code,
                result.attempts,
            )
            return {
                "status": "FAILED",
                "structuredResult": {},
                "confidence": 0.0,
                "errors": [
                    {
                        "code": result.error_code or "EXT_PARSE_FAILED",
                        "message": "Parse provider call failed",
                    }
                ],
                "meta": {"attempts": result.attempts},
            }
        structured = result.payload.get("structuredResult")
        if not isinstance(structured, dict):
            LOGGER.error(
                "Parse provider returned invalid payload type: %s",
                type(structured).__name__,
            )
            return {
                "status": "FAILED",
                "structuredResult": {},
                "confidence": 0.0,
                "errors": [
                    {
                        "code": "BIZ_INVALID_PARSE_PAYLOAD",
                        "message": "Parse provider returned invalid payload",
                    }
                ],
                "meta": {"attempts": result.attempts},
            }
        confidence = result.payload.get("confidence", 0.0)
        try:
            confidence_score = float(confidence)
        except (TypeError, ValueError):
            confidence_score = 0.0
        final: dict[str, Any] = {
            "status": "SUCCESS",
            "structuredResult": structured,
            "confidence": max(0.0, min(1.0, confidence_score)),
            "errors": [],
            "meta": {
                "attempts": result.attempts,
                "modelMeta": result.payload.get("modelMeta", {}),
            },
        }
        LOGGER.info(
            "Parse task succeeded: jobId=%s confidence=%s attempts=%s",
            payload.get("jobId"),
            final.get("confidence"),
            result.attempts,
        )
        return final
