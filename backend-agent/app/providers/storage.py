from __future__ import annotations

# pyright: reportMissingImports=false

import logging
import os
from typing import Any

import oss2  # type: ignore[import-not-found]


LOGGER = logging.getLogger(__name__)

MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024  # 20 MB guard for OSS downloads


class OSSError(Exception):
    """Raised when an OSS storage operation fails."""

    def __init__(self, message: str, *, code: str) -> None:
        super().__init__(message)
        self.code = code


class OSSStorageService:
    """Manages file downloads from Alibaba Cloud OSS."""

    def __init__(self) -> None:
        endpoint = os.getenv("OSS_ENDPOINT", os.getenv("S3_ENDPOINT", "")).strip()
        if endpoint and not endpoint.startswith(("http://", "https://")):
            endpoint = f"https://{endpoint}"
        self._endpoint = endpoint
        self._bucket_name = os.getenv("OSS_BUCKET", "").strip()
        self._access_key_id = os.getenv(
            "OSS_ACCESS_KEY_ID", os.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID", "")
        ).strip()
        self._access_key_secret = os.getenv(
            "OSS_ACCESS_KEY_SECRET", os.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "")
        ).strip()

    @property
    def is_configured(self) -> bool:
        return bool(
            self._endpoint
            and self._bucket_name
            and self._access_key_id
            and self._access_key_secret
        )

    def download_bytes(self, object_key: str) -> bytes:
        """Download an object from OSS and return its full contents.

        Raises:
            OSSError: on configuration, empty-file, size-limit, or network errors.
        """
        if not self.is_configured:
            raise OSSError(
                "OSS credentials not configured",
                code="BIZ_OSS_NOT_CONFIGURED",
            )

        try:
            auth = oss2.Auth(self._access_key_id, self._access_key_secret)
            bucket = oss2.Bucket(auth, self._endpoint, self._bucket_name)
            response = bucket.get_object(object_key)
            raw = response.read()  # type: ignore[union-attr]
            data: bytes = raw if isinstance(raw, bytes) else b""
        except oss2.exceptions.OssError as exc:
            raise OSSError(
                f"OSS download failed for {object_key}: {exc}",
                code="EXT_OSS_UNAVAILABLE",
            ) from exc

        if not data:
            raise OSSError(
                f"OSS object is empty: {object_key}",
                code="BIZ_EMPTY_UPLOAD_FILE",
            )
        if len(data) > MAX_DOWNLOAD_BYTES:
            raise OSSError(
                f"OSS object too large ({len(data)} bytes): {object_key}",
                code="BIZ_FILE_TOO_LARGE",
            )
        return data
