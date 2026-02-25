from __future__ import annotations

import logging
import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel

from app.mq.consumer import AgentMqConsumer
from app.providers.gateway import ProviderGateway
from app.utils import extract_error_codes
from app.workers.generate_worker import GenerateWorker
from app.workers.parse_worker import ParseWorker


class TaskPayload(BaseModel):
    payload: dict[str, Any]


LOGGER = logging.getLogger(__name__)


def configure_logging() -> None:
    level_name = os.getenv("LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


configure_logging()

gateway = ProviderGateway()
parse_worker = ParseWorker(gateway)
generate_worker = GenerateWorker(gateway)
mq_consumer = AgentMqConsumer(parse_worker.handle, generate_worker.handle)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    LOGGER.info(
        "Agent starting with MQ_CONSUMER_ENABLED=%s",
        os.getenv("MQ_CONSUMER_ENABLED", "true"),
    )
    LOGGER.info(
        "Agent config: rabbitmq_set=%s oss_set=%s gemini_set=%s",
        bool(os.getenv("RABBITMQ_URL")),
        bool(
            os.getenv("OSS_ENDPOINT")
            and os.getenv("OSS_BUCKET")
            and os.getenv("OSS_ACCESS_KEY_ID")
            and os.getenv("OSS_ACCESS_KEY_SECRET")
        ),
        bool(os.getenv("GOOGLE_API_KEY") or os.getenv("GEMINI_API_KEY")),
    )
    if os.getenv("MQ_CONSUMER_ENABLED", "true").lower() == "true":
        try:
            await mq_consumer.start()
        except Exception as exc:  # pragma: no cover - defensive startup fallback
            LOGGER.exception("Failed to start MQ consumer", exc_info=exc)

    yield

    try:
        await mq_consumer.close()
    except Exception:
        LOGGER.exception("Failed to close MQ consumer")


app = FastAPI(title="medical-agent-worker", version="0.1.0", lifespan=lifespan)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/internal/parse")
async def parse_task(task: TaskPayload) -> dict[str, Any]:
    LOGGER.info("/internal/parse invoked: keys=%s", sorted(task.payload.keys()))
    result = await parse_worker.handle(task.payload)
    LOGGER.info(
        "/internal/parse finished: status=%s error_codes=%s",
        result.get("status"),
        extract_error_codes(result),
    )
    return {"code": "OK", "message": "success", "data": result}


@app.post("/internal/generate")
async def generate_task(task: TaskPayload) -> dict[str, Any]:
    LOGGER.info("/internal/generate invoked: keys=%s", sorted(task.payload.keys()))
    result = await generate_worker.handle(task.payload)
    LOGGER.info(
        "/internal/generate finished: status=%s error_codes=%s",
        result.get("status"),
        extract_error_codes(result),
    )
    return {"code": "OK", "message": "success", "data": result}
