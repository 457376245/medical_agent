from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_us3_timeline_reverse_order_contract_and_index_present() -> None:
    contract = read_text("specs/001-medical-agent-mvp/contracts/openapi.yaml")
    model = read_text("specs/001-medical-agent-mvp/data-model.md")
    timeline_service = read_text(
        "backend-java/src/main/java/com/medical/agent/application/TimelineService.java"
    )
    persistence_service = read_text(
        "backend-java/src/main/java/com/medical/agent/application/PersistenceService.java"
    )
    assert_contains_all(contract, ["/timeline:", "reverse-chronological"])
    assert_contains_all(model, ["records(user_id, record_date desc)"])
    assert_contains_all(timeline_service, ["listTimelineBatches", "listBatchRecords"])
    assert_contains_all(
        persistence_service,
        ["left join disease_profiles", "order by latest_record_at desc"],
    )
