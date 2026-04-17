from __future__ import annotations

from dataclasses import dataclass
import logging
import random
import re
import ssl
import time
from typing import Any

from app.providers.llm import LLMError, LLMService
from app.providers.storage import OSSError
from app.utils import read_float_env, read_int_env


LOGGER = logging.getLogger(__name__)


@dataclass
class ProviderResponse:
    success: bool
    payload: dict[str, Any]
    error_code: str | None = None
    attempts: int = 1


class ProviderGateway:
    """弹性编排器 —— 重试、退避、错误分类。

    将实际工作委托给 ``LLMService``（内部使用 ``OSSStorageService`` 和 ``DocumentParser``）。
    """

    def __init__(self, llm: LLMService | None = None) -> None:
        self._llm = llm or LLMService()
        self._provider_max_attempts = read_int_env("PROVIDER_MAX_ATTEMPTS", 4, 1)
        self._provider_backoff_base_seconds = read_float_env(
            "PROVIDER_BACKOFF_BASE_SECONDS", 1.0, 0.1
        )
        self._provider_backoff_factor = read_float_env(
            "PROVIDER_BACKOFF_FACTOR", 2.0, 1.0
        )
        self._provider_backoff_jitter_seconds = read_float_env(
            "PROVIDER_BACKOFF_JITTER_SECONDS", 0.3, 0.0
        )

    # ------------------------------------------------------------------
    # 公共 API
    # ------------------------------------------------------------------

    @property
    def llm(self) -> LLMService:
        return self._llm

    def execute_with_resilience(
        self, operation: str, payload: dict[str, Any]
    ) -> ProviderResponse:
        max_attempts = self._provider_max_attempts
        for attempt in range(1, max_attempts + 1):
            try:
                # --- 测试模拟钩子 ---
                simulation = str(payload.get("simulate", ""))
                if simulation == "timeout":
                    raise TimeoutError("Provider timeout")
                if simulation == "external_error":
                    raise ConnectionError("External provider unavailable")
                if simulation == "biz_error":
                    raise ValueError("BIZ_INVALID_INPUT")

                model_name = self._llm.model_for_attempt(operation, attempt)
                LOGGER.info(
                    "Provider call started: operation=%s attempt=%s model=%s",
                    operation,
                    attempt,
                    model_name,
                )

                if operation == "parse":
                    result = self._llm.parse(payload, model_name, attempt)
                elif operation == "generate":
                    result = self._llm.generate(payload, model_name, attempt)
                else:
                    raise ValueError("BIZ_UNSUPPORTED_OPERATION")

                LOGGER.info(
                    "Provider call succeeded: operation=%s attempt=%s",
                    operation,
                    attempt,
                )
                return ProviderResponse(success=True, payload=result, attempts=attempt)

            # -- OSS 相关错误 --
            except OSSError as exc:
                LOGGER.warning(
                    "OSS error: operation=%s attempt=%s code=%s message=%s",
                    operation,
                    attempt,
                    exc.code,
                    str(exc),
                )
                if exc.code.startswith("BIZ_"):
                    # 业务级错误：配置/空内容/文件过大 —— 不重试
                    return ProviderResponse(
                        success=False,
                        payload={"operation": operation, "input": payload},
                        error_code=exc.code,
                        attempts=attempt,
                    )
                # EXT_OSS_UNAVAILABLE —— 可重试
                if attempt >= max_attempts:
                    return ProviderResponse(
                        success=False,
                        payload={"operation": operation, "input": payload},
                        error_code=exc.code,
                        attempts=attempt,
                    )
                self._sleep_before_retry(attempt)

            # -- LLM 相关错误 --
            except LLMError as exc:
                LOGGER.warning(
                    "LLM error: operation=%s attempt=%s code=%s message=%s",
                    operation,
                    attempt,
                    exc.code,
                    str(exc),
                )
                if exc.code.startswith("BIZ_"):
                    return ProviderResponse(
                        success=False,
                        payload={"operation": operation, "input": payload},
                        error_code=exc.code,
                        attempts=attempt,
                    )
                if attempt >= max_attempts:
                    return ProviderResponse(
                        success=False,
                        payload={"operation": operation, "input": payload},
                        error_code=exc.code,
                        attempts=attempt,
                    )
                self._sleep_before_retry(attempt)

            # -- 业务校验错误（带 BIZ_ 前缀的 ValueError）--
            except ValueError as exc:
                LOGGER.warning(
                    "Provider business error: operation=%s attempt=%s code=%s message=%s",
                    operation,
                    attempt,
                    self._extract_biz_code(str(exc)),
                    str(exc),
                )
                return ProviderResponse(
                    success=False,
                    payload={"operation": operation, "input": payload},
                    error_code=self._extract_biz_code(str(exc)),
                    attempts=attempt,
                )

            # -- 超时错误 --
            except TimeoutError as exc:
                LOGGER.warning(
                    "Provider timeout: operation=%s attempt=%s message=%s",
                    operation,
                    attempt,
                    str(exc),
                )
                if attempt >= max_attempts:
                    return ProviderResponse(
                        success=False,
                        payload={"operation": operation, "input": payload},
                        error_code="EXT_TIMEOUT",
                        attempts=attempt,
                    )
                self._sleep_before_retry(attempt)

            # -- 网络/连接错误 --
            except ConnectionError as exc:
                LOGGER.warning(
                    "Provider connectivity error: operation=%s attempt=%s error=%s",
                    operation,
                    attempt,
                    str(exc),
                )
                if attempt >= max_attempts:
                    return ProviderResponse(
                        success=False,
                        payload={"operation": operation, "input": payload},
                        error_code=(
                            "EXT_TIMEOUT"
                            if self._is_timeout_error(exc)
                            else "EXT_PROVIDER_UNAVAILABLE"
                        ),
                        attempts=attempt,
                    )
                self._sleep_before_retry(attempt)

            # -- 防御性兜底 --
            except Exception as exc:  # pragma: no cover
                error_code = self._classify_unknown_error(exc)
                LOGGER.exception(
                    "Provider unexpected error: operation=%s attempt=%s code=%s",
                    operation,
                    attempt,
                    error_code,
                )
                if attempt >= max_attempts:
                    return ProviderResponse(
                        success=False,
                        payload={"operation": operation, "input": payload},
                        error_code=error_code,
                        attempts=attempt,
                    )
                self._sleep_before_retry(attempt)

        return ProviderResponse(
            success=False,
            payload={"operation": operation, "input": payload},
            error_code="EXT_UNKNOWN",
            attempts=max_attempts,
        )

    # ------------------------------------------------------------------
    # 错误分类辅助方法
    # ------------------------------------------------------------------

    def _classify_unknown_error(self, exc: Exception) -> str:
        if self._is_timeout_error(exc):
            return "EXT_TIMEOUT"
        if self._is_connectivity_error(exc):
            return "EXT_PROVIDER_UNAVAILABLE"
        return "EXT_UNKNOWN"

    def _is_timeout_error(self, exc: Exception) -> bool:
        timeout_markers = (
            "timeout",
            "timed out",
            "deadline",
            "read operation timed out",
            "readtimeout",
            "connecttimeout",
        )
        for current in self._iter_exception_chain(exc):
            type_name = type(current).__name__.lower()
            if "timeout" in type_name:
                return True
            text = str(current).lower()
            if any(marker in text for marker in timeout_markers):
                return True
        return False

    def _is_connectivity_error(self, exc: Exception) -> bool:
        connectivity_markers = (
            "unexpected eof while reading",
            "eof occurred in violation of protocol",
            "connection reset by peer",
            "connection aborted",
            "connection refused",
            "name or service not known",
            "temporary failure in name resolution",
            "network is unreachable",
            "proxy error",
            "tls",
            "ssl",
        )
        connectivity_type_names = {
            "connecterror",
            "networkerror",
            "remoteprotocolerror",
            "proxyerror",
            "sslerror",
            "tlserror",
        }
        for current in self._iter_exception_chain(exc):
            if isinstance(current, (ConnectionError, ssl.SSLError)):
                return True
            type_name = type(current).__name__.lower()
            if type_name in connectivity_type_names:
                return True
            text = str(current).lower()
            if any(marker in text for marker in connectivity_markers):
                return True
        return False

    @staticmethod
    def _iter_exception_chain(exc: Exception) -> list[BaseException]:
        chain: list[BaseException] = []
        seen: set[int] = set()
        current: BaseException | None = exc
        while current is not None and id(current) not in seen:
            chain.append(current)
            seen.add(id(current))
            current = current.__cause__ or current.__context__
        return chain

    @staticmethod
    def _extract_biz_code(message: str) -> str:
        match = re.search(r"BIZ_[A-Z0-9_]+", message)
        if match:
            return match.group(0)
        return "BIZ_PROVIDER_FAILED"

    def _sleep_before_retry(self, attempt: int) -> None:
        if attempt >= self._provider_max_attempts:
            return
        exp = self._provider_backoff_factor ** (attempt - 1)
        jitter = random.uniform(0, self._provider_backoff_jitter_seconds)
        sleep_seconds = (self._provider_backoff_base_seconds * exp) + jitter
        time.sleep(sleep_seconds)