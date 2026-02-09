import logging
import os

from fastapi import FastAPI
from pydantic import BaseModel

from app.mq.consumer import AgentMqConsumer
from app.workers.generate_worker import GenerateWorker
from app.workers.parse_worker import ParseWorker


class TaskPayload(BaseModel):
    payload: dict


app = FastAPI(title="medical-agent-worker", version="0.1.0")
logger = logging.getLogger(__name__)


def configure_logging() -> None:
    level_name = os.getenv("LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


configure_logging()

parse_worker = ParseWorker()
generate_worker = GenerateWorker()
mq_consumer = AgentMqConsumer(parse_worker.handle, generate_worker.handle)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}


@app.on_event("startup")
async def startup_event() -> None:
    logger.info(
        "Agent starting with MQ_CONSUMER_ENABLED=%s",
        os.getenv("MQ_CONSUMER_ENABLED", "true"),
    )
    logger.info(
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
            logger.exception("Failed to start MQ consumer", exc_info=exc)


@app.on_event("shutdown")
async def shutdown_event() -> None:
    try:
        await mq_consumer.close()
    except Exception:
        logger.exception("Failed to close MQ consumer")


@app.post("/internal/parse")
async def parse_task(task: TaskPayload) -> dict:
    logger.info("/internal/parse invoked: keys=%s", sorted(task.payload.keys()))
    result = await parse_worker.handle(task.payload)
    logger.info(
        "/internal/parse finished: status=%s error_codes=%s",
        result.get("status"),
        [
            item.get("code")
            for item in result.get("errors", [])
            if isinstance(item, dict)
        ],
    )
    return {"code": "OK", "message": "success", "data": result}


@app.post("/internal/generate")
async def generate_task(task: TaskPayload) -> dict:
    logger.info("/internal/generate invoked: keys=%s", sorted(task.payload.keys()))
    result = await generate_worker.handle(task.payload)
    logger.info(
        "/internal/generate finished: status=%s error_codes=%s",
        result.get("status"),
        [
            item.get("code")
            for item in result.get("errors", [])
            if isinstance(item, dict)
        ],
    )
    return {"code": "OK", "message": "success", "data": result}
