from __future__ import annotations

# pyright: reportMissingImports=false

import logging
import os
from typing import Any

import oss2  # type: ignore[import-not-found]


LOGGER = logging.getLogger(__name__)

MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024  # OSS 下载大小限制 20 MB


class OSSError(Exception):
    """OSS 存储操作失败时抛出的异常。"""

    def __init__(self, message: str, *, code: str) -> None:
        super().__init__(message)
        self.code = code


class OSSStorageService:
    """管理阿里云 OSS 文件下载。"""

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
        """检查 OSS 是否已配置。"""
        return bool(
            self._endpoint
            and self._bucket_name
            and self._access_key_id
            and self._access_key_secret
        )

    def download_bytes(self, object_key: str) -> bytes:
        """从 OSS 下载对象并返回完整内容。

        抛出:
            OSSError: 配置错误、空文件、大小超限或网络错误。
        """
        if not self.is_configured:
            raise OSSError(
                "OSS 凭证未配置",
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
                f"OSS 下载失败 {object_key}: {exc}",
                code="EXT_OSS_UNAVAILABLE",
            ) from exc

        if not data:
            raise OSSError(
                f"OSS 对象为空: {object_key}",
                code="BIZ_EMPTY_UPLOAD_FILE",
            )
        if len(data) > MAX_DOWNLOAD_BYTES:
            raise OSSError(
                f"OSS 对象过大 ({len(data)} 字节): {object_key}",
                code="BIZ_FILE_TOO_LARGE",
            )
        return data