from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_kpi_metrics_targets_are_defined_in_spec() -> None:
    spec = read_text("specs/001-medical-agent-mvp/spec.md")
    assert_contains_all(
        spec, ["SC-001", "SC-002", "SC-003", "SC-004", "SC-005", "SC-006"]
    )
