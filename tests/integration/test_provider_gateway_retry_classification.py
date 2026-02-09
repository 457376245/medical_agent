from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "backend-agent"))

from app.providers import gateway as gateway_module
from app.providers.gateway import ProviderGateway


def test_ssl_eof_error_is_treated_as_retryable_connectivity_issue(
    monkeypatch,
) -> None:
    gateway = ProviderGateway()
    monkeypatch.setattr(gateway, "_provider_max_attempts", 2)
    monkeypatch.setattr(gateway, "_sleep_before_retry", lambda _attempt: None)

    class ConnectError(Exception):
        pass

    def raise_ssl_eof(_payload: dict, _model_name: str, _attempt: int) -> dict:
        try:
            raise ConnectError(
                "[SSL: UNEXPECTED_EOF_WHILE_READING] EOF occurred in violation of protocol"
            )
        except ConnectError as inner:
            raise RuntimeError("parse invoke failed") from inner

    monkeypatch.setattr(gateway, "_parse_with_langchain", raise_ssl_eof)
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


def test_chat_model_alternates_trust_env_after_first_attempt(monkeypatch) -> None:
    gateway = ProviderGateway()
    gateway._google_api_key = "dummy-key"
    gateway._gemini_proxy = ""
    gateway._gemini_trust_env = False
    gateway._gemini_retry_with_env_proxy = True

    trust_env_values: list[bool] = []

    class DummyChatModel:
        def __init__(self, **kwargs):
            trust_env_values.append(bool(kwargs["client_args"]["trust_env"]))

    monkeypatch.setattr(gateway_module, "ChatGoogleGenerativeAI", DummyChatModel)
    gateway._chat_model("gemini-2.5-flash", 1)
    gateway._chat_model("gemini-2.5-flash", 2)
    gateway._chat_model("gemini-2.5-flash", 3)
    gateway._chat_model("gemini-2.5-flash", 4)

    assert trust_env_values == [False, True, False, True]
