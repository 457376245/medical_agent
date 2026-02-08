from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_structured_result_patch_contract_has_versioned_payload() -> None:
    openapi = read_text("specs/001-medical-agent-mvp/contracts/openapi.yaml")
    assert_contains_all(
        openapi,
        [
            "/records/{recordId}/structured-result:",
            "operationId: patchStructuredResult",
            "required: [version, payload]",
        ],
    )
