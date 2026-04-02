from __future__ import annotations

from app.utils import normalize_openai_base_url


def test_normalize_openai_base_url_appends_v1_when_missing() -> None:
    assert normalize_openai_base_url("http://127.0.0.1:8317") == "http://127.0.0.1:8317/v1"
    assert normalize_openai_base_url("http://127.0.0.1:8317/") == "http://127.0.0.1:8317/v1"


def test_normalize_openai_base_url_keeps_existing_v1() -> None:
    assert normalize_openai_base_url("http://127.0.0.1:8317/v1") == "http://127.0.0.1:8317/v1"
