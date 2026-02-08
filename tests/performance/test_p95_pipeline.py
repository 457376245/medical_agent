from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_p95_pipeline_target_is_declared_in_plan_and_spec() -> None:
    spec = read_text("specs/001-medical-agent-mvp/spec.md")
    plan = read_text("specs/001-medical-agent-mvp/plan.md")
    assert_contains_all(spec, ["P95", "90 秒"])
    assert_contains_all(plan, ["P95 <= 90s"])
