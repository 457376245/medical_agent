from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_upload_dialog_closes_after_background_parse_job_creation() -> None:
    top_bar = read_text("frontend/src/components/layout/UserTopBar.tsx")
    assert_contains_all(
        top_bar,
        [
            "解析任务已在后台执行",
            "closeDialog();",
            "router.refresh();",
        ],
    )
    assert "waitForParseTerminalStatus" not in top_bar


def test_parse_failure_retry_scheduler_exists() -> None:
    scheduler = read_text(
        "backend-java/src/main/java/com/medical/agent/infrastructure/scheduler/ParseRetryScheduler.java"
    )
    assert_contains_all(
        scheduler,
        [
            "@Scheduled",
            "listFailedParseJobsForRetry",
            "markParseJobRetrying",
            "parseRequestPublisher.publish",
        ],
    )


def test_analysis_generation_requires_successful_non_empty_parse_result() -> None:
    analysis_service = read_text(
        "backend-java/src/main/java/com/medical/agent/application/ReportAnalysisService.java"
    )
    timeline_view = read_text(
        "frontend/src/components/timeline/DiseaseTimelineView.tsx"
    )
    assert_contains_all(
        analysis_service,
        [
            "AnalysisNotReadyException",
            "isParseResultReadyForAnalysis",
            "parseStatus",
        ],
    )
    assert_contains_all(
        timeline_view,
        [
            'parseStatus === "SUCCESS"',
            "hasStructuredFields",
            "loadRecordAnalysis",
        ],
    )
