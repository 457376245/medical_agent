from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "backend-agent"))

from app.providers.llm import LLMError, LLMService
from app.providers.gateway import ProviderGateway


def test_ssl_eof_error_is_treated_as_retryable_connectivity_issue(
    monkeypatch,
) -> None:
    class StubLLM:
        def model_for_attempt(self, operation: str, attempt: int) -> str:
            return f"{operation}-{attempt}"

        def parse(self, payload: dict, model_name: str, attempt: int) -> dict:
            del payload, model_name, attempt
            class ConnectError(Exception):
                pass

            try:
                raise ConnectError(
                    "[SSL: UNEXPECTED_EOF_WHILE_READING] EOF occurred in violation of protocol"
                )
            except ConnectError as inner:
                raise RuntimeError("parse invoke failed") from inner

        def generate(self, payload: dict, model_name: str, attempt: int) -> dict:
            del payload, model_name, attempt
            return {"content": "ok"}

    gateway = ProviderGateway(llm=StubLLM())
    monkeypatch.setattr(gateway, "_provider_max_attempts", 2)
    monkeypatch.setattr(gateway, "_sleep_before_retry", lambda _attempt: None)
    response = gateway.execute_with_resilience(
        "parse",
        {"assetRefs": [{"objectKey": "report.pdf", "fileType": "PDF"}]},
    )
    assert not response.success
    assert response.error_code == "EXT_PROVIDER_UNAVAILABLE"
    assert response.attempts == 2


def test_timeout_detection_supports_nested_exception_chain() -> None:
    gateway = ProviderGateway()

    class ReadTimeout(Exception):
        pass

    try:
        try:
            raise ReadTimeout("Read operation timed out")
        except ReadTimeout as inner:
            raise RuntimeError("wrapper exception") from inner
    except RuntimeError as exc:
        assert gateway._is_timeout_error(exc)


def test_model_for_attempt_is_operation_aware(monkeypatch) -> None:
    monkeypatch.setenv("OPENAI_PARSE_MODEL", "parse-model")
    monkeypatch.setenv("OPENAI_GENERATE_MODEL", "generate-model")
    monkeypatch.setenv("OPENAI_FALLBACK_MODEL", "fallback-model")

    service = LLMService()

    assert service.model_for_attempt("parse", 1) == "parse-model"
    assert service.model_for_attempt("generate", 1) == "generate-model"
    assert service.model_for_attempt("parse", 2) == "fallback-model"
