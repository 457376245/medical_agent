"""最小 OpenAI 兼容服务连通性测试脚本。"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any


DEFAULT_PROMPT = "请只回复 pong"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="测试 OpenAI 兼容服务是否可调通。"
    )
    parser.add_argument(
        "--base-url",
        default=os.getenv("OPENAI_BASE_URL", "").strip(),
        help="服务地址，例如 http://127.0.0.1:8317/",
    )
    parser.add_argument(
        "--api-key",
        default=os.getenv("OPENAI_API_KEY", ""),
        help="OpenAI 兼容服务密钥，优先建议通过环境变量传入。",
    )
    parser.add_argument(
        "--model",
        default=os.getenv("OPENAI_MODEL", "").strip(),
        help="可选，显式指定模型名；未提供时优先从 /v1/models 自动选择。",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=float(os.getenv("OPENAI_TIMEOUT", "30")),
        help="请求超时时间，单位秒，默认 30。",
    )
    parser.add_argument(
        "--prompt",
        default=DEFAULT_PROMPT,
        help="测试对话提示词。",
    )
    return parser


def normalize_base_url(base_url: str) -> str:
    normalized = base_url.strip().rstrip("/")
    if not normalized:
        raise ValueError("缺少服务地址，请通过 --base-url 或 OPENAI_BASE_URL 提供。")
    parsed = urllib.parse.urlparse(normalized)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ValueError("服务地址格式不正确，示例：http://127.0.0.1:8317/")
    if not normalized.endswith("/v1"):
        normalized = f"{normalized}/v1"
    return normalized


def mask_api_key(api_key: str) -> str:
    if len(api_key) <= 8:
        return "*" * len(api_key)
    return f"{api_key[:4]}...{api_key[-4:]}"


def send_json_request(
    method: str,
    url: str,
    api_key: str,
    timeout: float,
    payload: dict[str, Any] | None = None,
) -> tuple[int, dict[str, Any]]:
    data = None
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Accept": "application/json",
    }
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

    request = urllib.request.Request(url=url, data=data, headers=headers, method=method)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(request, timeout=timeout) as response:
            raw_body = response.read().decode("utf-8")
            parsed_body = json.loads(raw_body) if raw_body else {}
            return response.status, parsed_body
    except urllib.error.HTTPError as exc:
        raw_body = exc.read().decode("utf-8", errors="replace")
        try:
            parsed_body = json.loads(raw_body) if raw_body else {}
        except json.JSONDecodeError:
            parsed_body = {"raw_error": raw_body}
        return exc.code, parsed_body


def fetch_model(base_url: str, api_key: str, timeout: float, preferred: str) -> str:
    if preferred:
        return preferred

    status_code, body = send_json_request(
        method="GET",
        url=f"{base_url}/models",
        api_key=api_key,
        timeout=timeout,
    )
    if status_code >= 400:
        raise RuntimeError(
            f"/models 调用失败，HTTP {status_code}，响应：{json.dumps(body, ensure_ascii=False)}"
        )

    models = body.get("data")
    if not isinstance(models, list) or not models:
        raise RuntimeError("`/models` 未返回可用模型，请通过 --model 显式指定模型名。")

    first_model = models[0]
    model_id = first_model.get("id") if isinstance(first_model, dict) else None
    if not isinstance(model_id, str) or not model_id.strip():
        raise RuntimeError("`/models` 返回格式不符合预期，未找到模型 id。")
    return model_id.strip()


def run_smoke_test(
    base_url: str,
    api_key: str,
    model: str,
    timeout: float,
    prompt: str,
) -> int:
    status_code, body = send_json_request(
        method="POST",
        url=f"{base_url}/chat/completions",
        api_key=api_key,
        timeout=timeout,
        payload={
            "model": model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0,
        },
    )
    if status_code >= 400:
        raise RuntimeError(
            f"/chat/completions 调用失败，HTTP {status_code}，响应：{json.dumps(body, ensure_ascii=False)}"
        )

    choices = body.get("choices")
    if not isinstance(choices, list) or not choices:
        raise RuntimeError("对话接口响应中未找到 choices。")

    first_choice = choices[0]
    message = first_choice.get("message") if isinstance(first_choice, dict) else None
    content = message.get("content") if isinstance(message, dict) else None
    if not isinstance(content, str) or not content.strip():
        raise RuntimeError("对话接口响应中未找到有效回复内容。")

    print("连通性测试成功。")
    print(f"模型：{model}")
    print(f"回复：{content.strip()}")
    return 0


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    try:
        base_url = normalize_base_url(args.base_url)
    except ValueError as exc:
        parser.error(str(exc))

    api_key = args.api_key.strip()
    if not api_key:
        parser.error("缺少 API Key，请通过 --api-key 或 OPENAI_API_KEY 提供。")

    print(f"目标服务：{base_url}")
    print(f"API Key：{mask_api_key(api_key)}")

    try:
        model = fetch_model(
            base_url=base_url,
            api_key=api_key,
            timeout=args.timeout,
            preferred=args.model.strip(),
        )
        print(f"使用模型：{model}")
        return run_smoke_test(
            base_url=base_url,
            api_key=api_key,
            model=model,
            timeout=args.timeout,
            prompt=args.prompt,
        )
    except Exception as exc:  # noqa: BLE001 - 统一输出测试失败原因
        print(f"连通性测试失败：{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
