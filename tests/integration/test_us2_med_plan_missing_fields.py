from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_us2_med_plan_requires_disclaimer_and_reconfirm_before_save() -> None:
    panel = read_text("frontend/src/components/generation/MedicationPlanPanel.tsx")
    confirm = read_text(
        "frontend/src/components/generation/MedicationPlanConfirmDialog.tsx"
    )
    assert_contains_all(panel, ["disclaimer", "Medication Plan Draft"])
    assert_contains_all(confirm, ["reconfirm", "Confirm and Save"])
