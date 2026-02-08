from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_us1_failure_and_retry_states_declared() -> None:
    openapi = read_text("specs/001-medical-agent-mvp/contracts/openapi.yaml")
    assert_contains_all(
        openapi,
        [
            "FAILED",
            "RETRYING",
            "DEAD_LETTER",
            "errorCode",
        ],
    )
