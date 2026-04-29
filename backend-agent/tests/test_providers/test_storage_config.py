from __future__ import annotations

from app.providers.storage import OSSStorageService


def test_storage_service_reads_java_oss_env_names(monkeypatch) -> None:
    monkeypatch.delenv("OSS_ENDPOINT", raising=False)
    monkeypatch.delenv("OSS_BUCKET", raising=False)
    monkeypatch.delenv("OSS_ACCESS_KEY_ID", raising=False)
    monkeypatch.delenv("OSS_ACCESS_KEY_SECRET", raising=False)

    monkeypatch.setenv("APP_OSS_ENDPOINT", "https://oss-cn-shanghai.aliyuncs.com")
    monkeypatch.setenv("APP_OSS_BUCKET", "medical-agent-hjh")
    monkeypatch.setenv("APP_OSS_ACCESS_KEY_ID", "test-access-key-id")
    monkeypatch.setenv("APP_OSS_ACCESS_KEY_SECRET", "test-access-key-secret")

    storage = OSSStorageService()

    assert storage.is_configured is True
