from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_us2_med_plan_generation_path_wired() -> None:
    worker_text = read_text("backend-agent/app/workers/generate_worker.py")
    api_text = read_text(
        "backend-java/src/main/java/com/medical/agent/api/MedicationPlanController.java"
    )
    assert_contains_all(
        worker_text, ["execute_with_resilience", "status", "modelMeta", "gateway"]
    )
    assert_contains_all(
        api_text, ["generate-medication-plan", "Idempotency-Key", "QUEUED"]
    )
