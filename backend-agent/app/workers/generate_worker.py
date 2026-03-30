from __future__ import annotations

import asyncio
from typing import Any

from app.providers.gateway import ProviderGateway


class GenerateWorker:
    def __init__(
        self,
        gateway: ProviderGateway | None = None,
        *,
        semaphore: asyncio.Semaphore | None = None,
    ) -> None:
        self.gateway = gateway or ProviderGateway()
        self._semaphore = semaphore

    async def handle(self, payload: dict[str, Any]) -> dict[str, Any]:
        if not payload.get("recordId"):
            return {
                "status": "FAILED",
                "type": payload.get("type", "SUMMARY"),
                "content": "",
                "modelMeta": {"provider": "gateway", "framework": "langchain-v1"},
                "errors": [
                    {"code": "BIZ_MISSING_RECORD_ID", "message": "recordId is required"}
                ],
            }

        if self._semaphore is not None:
            async with self._semaphore:
                return await self._execute(payload)
        return await self._execute(payload)

    async def _execute(self, payload: dict[str, Any]) -> dict[str, Any]:
        result = await asyncio.to_thread(
            self.gateway.execute_with_resilience,
            "generate",
            payload,
        )
        if not result.success:
            return {
                "status": "FAILED",
                "type": payload.get("type", "SUMMARY"),
                "content": "",
                "modelMeta": {"provider": "gateway", "attempts": result.attempts},
                "errors": [
                    {
                        "code": result.error_code or "EXT_GENERATE_FAILED",
                        "message": "Generate provider call failed",
                    }
                ],
            }

        output_type = str(result.payload.get("type", payload.get("type", "SUMMARY")))
        content = str(result.payload.get("content", "")).strip()
        if not content:
            return {
                "status": "FAILED",
                "type": output_type,
                "content": "",
                "modelMeta": {"provider": "gateway", "attempts": result.attempts},
                "errors": [
                    {
                        "code": "BIZ_EMPTY_GENERATION",
                        "message": "Generate provider returned empty content",
                    }
                ],
            }

        return {
            "status": "SUCCESS",
            "type": output_type,
            "content": content,
            "modelMeta": {
                "attempts": result.attempts,
                **(
                    result.payload.get("modelMeta", {})
                    if isinstance(result.payload.get("modelMeta", {}), dict)
                    else {}
                ),
            },
            "errors": [],
        }
