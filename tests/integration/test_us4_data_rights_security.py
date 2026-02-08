from pathlib import Path


def read_text(rel_path: str) -> str:
    root = Path(__file__).resolve().parents[2]
    return (root / rel_path).read_text(encoding="utf-8")


def assert_contains_all(text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"Missing required tokens: {missing}"


def test_us4_data_rights_endpoints_and_security_boundary_present() -> None:
    security = read_text(
        "backend-java/src/main/java/com/medical/agent/config/SecurityConfig.java"
    )
    export_api = read_text(
        "backend-java/src/main/java/com/medical/agent/api/ExportController.java"
    )
    delete_api = read_text(
        "backend-java/src/main/java/com/medical/agent/api/DeleteController.java"
    )
    request_service = read_text(
        "backend-java/src/main/java/com/medical/agent/application/DataRightsRequestService.java"
    )
    persistence_service = read_text(
        "backend-java/src/main/java/com/medical/agent/application/PersistenceService.java"
    )
    audit_service = read_text(
        "backend-java/src/main/java/com/medical/agent/infrastructure/audit/AuditLogService.java"
    )
    assert_contains_all(security, ["anyRequest().authenticated()"])
    assert_contains_all(export_api, ["/download", "createRequest", "getStatus"])
    assert_contains_all(delete_api, ["createRequest", "getStatus"])
    assert_contains_all(
        request_service, ["createDataRightsRequest", "getDataRightsRequest"]
    )
    assert_contains_all(
        persistence_service, ["insert into data_rights_requests", "download_url"]
    )
    assert_contains_all(audit_service, ["action", "resourceType", "requestId"])
