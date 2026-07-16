"""Agent API identity scope verified by the Java authority service."""

from __future__ import annotations

import asyncio
import hashlib
import json
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass

from fastapi import HTTPException, Request


@dataclass(frozen=True)
class AgentScope:
    tenant_id: str
    user_id: str
    patient_id: str

    @property
    def owner_key(self) -> str:
        value = f"{self.tenant_id}:{self.user_id}:{self.patient_id}"
        return hashlib.sha256(value.encode("utf-8")).hexdigest()

    def internal_headers(self) -> dict[str, str]:
        return {
            "X-Agent-Tenant-Id": self.tenant_id,
            "X-Agent-User-Id": self.user_id,
            "X-Agent-Patient-Id": self.patient_id,
        }


class AgentScopeClient:
    def __init__(
        self,
        *,
        base_url: str,
        context_path: str,
        timeout_seconds: float,
        api_key: str = "",
        api_key_header: str = "X-Internal-Api-Key",
    ) -> None:
        prefix = "/" + context_path.strip("/") if context_path else ""
        self._url = f"{base_url.rstrip('/')}{prefix}/scope/verify"
        self._attachments_url = f"{base_url.rstrip('/')}{prefix}/scope/attachments"
        self._timeout_seconds = timeout_seconds
        self._api_key = api_key.strip()
        self._api_key_header = api_key_header.strip() or "X-Internal-Api-Key"

    def verify(self, *, authorization: str, patient_id: str | None) -> AgentScope:
        headers = {"Authorization": authorization, "Accept": "application/json"}
        if patient_id:
            headers["X-Patient-Id"] = patient_id
        request = urllib.request.Request(self._url, headers=headers, method="GET")
        try:
            with urllib.request.urlopen(request, timeout=self._timeout_seconds) as response:
                payload = json.loads(response.read().decode("utf-8") or "{}")
        except urllib.error.HTTPError as error:
            status = error.code if error.code in {401, 403} else 503
            raise HTTPException(status_code=status, detail="agent scope verification failed") from error
        except Exception as error:
            raise HTTPException(status_code=503, detail="agent scope verification unavailable") from error
        try:
            return AgentScope(
                tenant_id=str(payload["tenantId"]),
                user_id=str(payload["userId"]),
                patient_id=str(payload["patientId"]),
            )
        except (KeyError, TypeError, ValueError) as error:
            raise HTTPException(status_code=503, detail="agent scope response invalid") from error

    def authorize_attachments(self, *, scope: AgentScope, object_keys: list[str]) -> frozenset[str]:
        if not object_keys:
            return frozenset()
        headers = {"Content-Type": "application/json", "Accept": "application/json", **scope.internal_headers()}
        if self._api_key:
            headers[self._api_key_header] = self._api_key
        request = urllib.request.Request(
            self._attachments_url,
            data=json.dumps({"objectKeys": object_keys}).encode("utf-8"),
            headers=headers,
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self._timeout_seconds) as response:
                payload = json.loads(response.read().decode("utf-8") or "{}")
        except Exception as error:
            raise HTTPException(status_code=503, detail="attachment authorization unavailable") from error
        values = payload.get("authorizedObjectKeys") if isinstance(payload, dict) else []
        return frozenset(str(value).strip() for value in values if str(value).strip())


async def require_agent_scope(request: Request) -> AgentScope:
    authorization = request.headers.get("Authorization", "").strip()
    if not authorization.startswith("Bearer ") or not authorization[7:].strip():
        raise HTTPException(status_code=401, detail="Bearer token required")
    client = getattr(request.app.state, "agent_scope_client", None)
    if client is None:
        raise HTTPException(status_code=503, detail="agent scope verifier unavailable")
    scope = await asyncio.to_thread(
        client.verify,
        authorization=authorization,
        patient_id=request.headers.get("X-Patient-Id", "").strip() or None,
    )
    request.state.agent_scope = scope
    return scope
