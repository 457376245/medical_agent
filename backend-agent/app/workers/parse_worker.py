from __future__ import annotations

import asyncio
import logging
from typing import Any

from app.providers.gateway import ProviderGateway


LOGGER = logging.getLogger(__name__)


class ParseWorker:
    def __init__(
        self,
        gateway: ProviderGateway | None = None,
        *,
        semaphore: asyncio.Semaphore | None = None,
    ) -> None:
        self.gateway = gateway or ProviderGateway()
        self._semaphore = semaphore

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

        if self._semaphore is not None:
            async with self._semaphore:
                return await self._execute(payload)
        return await self._execute(payload)

    async def _execute(self, payload: dict[str, Any]) -> dict[str, Any]:
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

        source_type = payload.get("sourceType")
        if not source_type:
            existing_categories = payload.get("existingCategories", [])
            fields = structured.get("fields", [])
            classified = await self._classify_report(fields, existing_categories)
            if classified:
                final["classifiedSourceType"] = classified
                LOGGER.info(
                    "Auto-classified report: jobId=%s category='%s'",
                    payload.get("jobId"),
                    classified,
                )

        return final

    async def _classify_report(
        self,
        fields: list[dict[str, Any]],
        existing_categories: list[str],
    ) -> str | None:
        """Call LLM to classify the report, returning a category name (max 5 chars)."""
        if not fields:
            return None
        try:
            return await asyncio.to_thread(
                self.gateway.llm.classify_report_category,
                fields,
                existing_categories,
            )
        except Exception:
            LOGGER.warning("Report auto-classification failed", exc_info=True)
            return None
