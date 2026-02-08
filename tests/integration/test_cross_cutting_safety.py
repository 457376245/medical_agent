from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_cross_cutting_safety_controls_present() -> None:
    redaction = read_text(
        "backend-java/src/main/java/com/medical/agent/config/LoggingRedactionConfig.java"
    )
    idempotency = read_text(
        "backend-java/src/main/java/com/medical/agent/infrastructure/idempotency/IdempotencyInterceptor.java"
    )
    assert_contains_all(redaction, ["redactionKeywords", "medical_text", "raw_content"])
    assert_contains_all(
        idempotency, ["Idempotency-Key", "SC_BAD_REQUEST", "requiresIdempotency"]
    )
