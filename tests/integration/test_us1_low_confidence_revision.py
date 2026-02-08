from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_us1_low_confidence_field_supports_source_evidence_and_revision_payload() -> (
    None
):
    schema_text = read_text("backend-agent/app/schemas/structured_result_v1.py")
    controller_text = read_text(
        "backend-java/src/main/java/com/medical/agent/api/StructuredResultController.java"
    )
    persistence_service = read_text(
        "backend-java/src/main/java/com/medical/agent/application/PersistenceService.java"
    )
    assert_contains_all(
        schema_text, ["class SourceEvidence", "confidence", "schema_version", "fields"]
    )
    assert_contains_all(
        controller_text, ["structured-result", "@PatchMapping", "patchStructuredResult"]
    )
    assert_contains_all(
        persistence_service,
        ["revision", "is_user_edited", "insert into structured_results"],
    )
