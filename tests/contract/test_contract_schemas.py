from pathlib import Path


def test_openapi_contract_exists() -> None:
    path = Path("specs/001-medical-agent-mvp/contracts/openapi.yaml")
    assert path.exists()
    assert "openapi: 3.0.3" in path.read_text(encoding="utf-8")


def test_asyncapi_contract_exists() -> None:
    path = Path("specs/001-medical-agent-mvp/contracts/asyncapi.yaml")
    assert path.exists()
    assert "asyncapi: 2.6.0" in path.read_text(encoding="utf-8")
