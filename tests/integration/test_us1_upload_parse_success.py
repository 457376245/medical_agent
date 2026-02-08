from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_us1_upload_parse_success_flow_wired() -> None:
    upload_controller = read_text(
        "backend-java/src/main/java/com/medical/agent/api/UploadController.java"
    )
    parse_controller = read_text(
        "backend-java/src/main/java/com/medical/agent/api/ParseJobController.java"
    )
    persistence_service = read_text(
        "backend-java/src/main/java/com/medical/agent/application/PersistenceService.java"
    )
    record_controller = read_text(
        "backend-java/src/main/java/com/medical/agent/api/RecordController.java"
    )
    assert_contains_all(
        upload_controller, ['@PostMapping("/presign")', "uploadUrl", "objectKey"]
    )
    assert_contains_all(
        parse_controller,
        ["createOrReuseParseJob", "getAndAdvanceParseJob"],
    )
    assert_contains_all(
        persistence_service,
        [
            "insert into parse_jobs",
            "insert into structured_results",
            "createGeneratedOutput",
            "summary",
            "structuredResult",
        ],
    )
    assert_contains_all(record_controller, ["fetchRecord", "data", "record"])
