from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_us1_upload_parse_success_flow_wired() -> None:
    ingestion_controller = read_text(
        "backend-java/src/main/java/com/medical/agent/api/IngestionController.java"
    )
    parse_service = read_text(
        "backend-java/src/main/java/com/medical/agent/application/service/ParseJobService.java"
    )
    parse_repository = read_text(
        "backend-java/src/main/java/com/medical/agent/infrastructure/persistence/jdbc/JdbcParseJobRepository.java"
    )
    record_controller = read_text(
        "backend-java/src/main/java/com/medical/agent/api/RecordController.java"
    )
    assert_contains_all(
        ingestion_controller,
        [
            '@PostMapping("/presign")',
            '@PostMapping("/assets")',
            '@PostMapping("/parse-jobs")',
        ],
    )
    assert_contains_all(
        parse_service,
        ["createOrReuseParseJob", "bindParseJobAssets"],
    )
    assert_contains_all(
        parse_repository,
        [
            "createOrReuseParseJob",
            "applyParseResult",
            "insert into parse_jobs",
        ],
    )
    assert_contains_all(record_controller, ["fetchRecord", "data", "record"])
