from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_encryption_controls_declared_in_config_and_plan() -> None:
    storage_cfg = read_text(
        "backend-java/src/main/java/com/medical/agent/config/StorageEncryptionConfig.java"
    )
    plan = read_text("specs/001-medical-agent-mvp/plan.md")
    assert_contains_all(
        storage_cfg,
        ["storage.encryption.enabled", "database.encryption.enabled", "@PostConstruct"],
    )
    assert_contains_all(plan, ["encrypted transport/storage"])
