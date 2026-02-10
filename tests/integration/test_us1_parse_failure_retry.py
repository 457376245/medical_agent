from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_us1_failure_and_retry_states_declared() -> None:
    scheduler = read_text(
        "backend-java/src/main/java/com/medical/agent/infrastructure/scheduler/ParseRetryScheduler.java"
    )
    repository = read_text(
        "backend-java/src/main/java/com/medical/agent/infrastructure/persistence/jdbc/JdbcParseJobRepository.java"
    )
    assert_contains_all(
        scheduler,
        [
            "listFailedParseJobsForRetry",
            "markParseJobRetrying",
            "markParseJobDeadLetter",
        ],
    )
    assert_contains_all(repository, ["FAILED", "RETRYING", "DEAD_LETTER", "error_code"])
