from __future__ import annotations

import asyncio
import logging
import os
from collections.abc import AsyncIterator
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from app.config import (
    CORS_ALLOW_ORIGINS,
    JAVA_AGENT_API_KEY,
    JAVA_AGENT_API_KEY_HEADER,
    JAVA_AGENT_CONTEXT_PATH,
    JAVA_AGENT_CONTEXT_TIMEOUT_SECONDS,
    JAVA_API_BASE_URL,
    LANGCHAIN_API_KEY,
    LANGCHAIN_PROJECT,
    LANGCHAIN_TRACING_V2,
    LLM_PROXY_MODE,
    OPENAI_API_KEY,
    OPENAI_BASE_URL,
)
from app.mq.consumer import AgentMqConsumer
from app.providers.document import DocumentParser
from app.providers.gateway import ProviderGateway
from app.providers.llm import LLMService
from app.providers.storage import OSSStorageService
from app.services.disease_profile_context import DiseaseProfileContextClient
from app.utils import configure_llm_proxy_env, extract_error_codes, read_int_env, to_bool
from app.workers.generate_worker import GenerateWorker
from app.workers.parse_worker import ParseWorker


class TaskPayload(BaseModel):
    payload: dict[str, Any]


LOGGER = logging.getLogger(__name__)

# Explicit thread-pool size so that blocking OSS downloads + LLM calls
# cannot exhaust the default executor (typically ~8 threads).
WORKER_THREAD_POOL_SIZE = int(os.getenv("WORKER_THREAD_POOL_SIZE", "16"))

# Cap concurrent file-processing operations to bound memory usage
# (each operation can hold up to MAX_DOWNLOAD_BYTES in memory).
MAX_CONCURRENT_TASKS = int(os.getenv("MAX_CONCURRENT_TASKS", "8"))


def configure_logging() -> None:
    level_name = os.getenv("LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


configure_logging()
configure_llm_proxy_env(LLM_PROXY_MODE, [])

# ---------------------------------------------------------------------------
# Dependency wiring — existing task-processing pipeline (unchanged)
# ---------------------------------------------------------------------------
storage = OSSStorageService()
document = DocumentParser()
llm = LLMService(storage=storage, document=document)
gateway = ProviderGateway(llm=llm)

# Concurrency semaphore shared by both workers
task_semaphore = asyncio.Semaphore(MAX_CONCURRENT_TASKS)

parse_worker = ParseWorker(gateway, semaphore=task_semaphore)
generate_worker = GenerateWorker(gateway, semaphore=task_semaphore)
mq_consumer = AgentMqConsumer(parse_worker.handle, generate_worker.handle)

# ---------------------------------------------------------------------------
# Dependency wiring — Agent tools (inject providers into tool modules)
# ---------------------------------------------------------------------------
from app.tools import document_parse as _tool_doc  # noqa: E402
from app.tools import disease_profile_context as _tool_context  # noqa: E402
from app.tools import text_generate as _tool_gen  # noqa: E402

_tool_doc.configure(gateway=gateway)
_tool_gen.configure(gateway=gateway)
_tool_context.configure(
    client=DiseaseProfileContextClient(
        base_url=JAVA_API_BASE_URL,
        context_path=JAVA_AGENT_CONTEXT_PATH,
        timeout_seconds=JAVA_AGENT_CONTEXT_TIMEOUT_SECONDS,
        api_key=JAVA_AGENT_API_KEY,
        api_key_header=JAVA_AGENT_API_KEY_HEADER,
    )
)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    # Install explicit thread pool on the running event loop
    loop = asyncio.get_running_loop()
    executor = ThreadPoolExecutor(
        max_workers=WORKER_THREAD_POOL_SIZE,
        thread_name_prefix="agent-worker",
    )
    loop.set_default_executor(executor)
    LOGGER.info(
        "Thread pool configured: max_workers=%s, max_concurrent_tasks=%s",
        WORKER_THREAD_POOL_SIZE,
        MAX_CONCURRENT_TASKS,
    )

    # --- Agent memory & graph setup ---
    from app.memory.checkpointer import create_checkpointer
    from app.memory.store import SqliteMemoryStore
    from app.agent.graph import build_graph

    checkpointer, _checkpointer_cm = await create_checkpointer()
    memory_store = SqliteMemoryStore()
    await memory_store.initialize()

    agent_graph = build_graph(checkpointer=checkpointer)

    # Expose on app.state so that api/ routers can access them
    app.state.agent_graph = agent_graph
    app.state.memory_store = memory_store
    app.state.checkpointer = checkpointer

    LOGGER.info("Agent graph and memory stores initialised")

    # --- LangSmith observability ---
    if LANGCHAIN_TRACING_V2 and LANGCHAIN_API_KEY:
        LOGGER.info(
            "LangSmith tracing ENABLED: project=%s", LANGCHAIN_PROJECT
        )
    elif LANGCHAIN_TRACING_V2:
        LOGGER.warning(
            "LANGCHAIN_TRACING_V2=true but LANGCHAIN_API_KEY is empty — tracing will not work"
        )
    else:
        LOGGER.info("LangSmith tracing disabled")

    # --- MQ consumer startup (existing) ---
    LOGGER.info(
        "Agent starting with MQ_CONSUMER_ENABLED=%s",
        os.getenv("MQ_CONSUMER_ENABLED", "true"),
    )
    LOGGER.info(
        "Agent config: rabbitmq_set=%s oss_set=%s openai_set=%s",
        bool(os.getenv("RABBITMQ_URL")),
        storage.is_configured,
        bool(OPENAI_BASE_URL and OPENAI_API_KEY),
    )
    if os.getenv("MQ_CONSUMER_ENABLED", "true").lower() == "true":
        try:
            await mq_consumer.start()
        except Exception as exc:  # pragma: no cover
            LOGGER.exception("Failed to start MQ consumer", exc_info=exc)

    yield

    # --- Shutdown ---
    try:
        await mq_consumer.close()
    except Exception:
        LOGGER.exception("Failed to close MQ consumer")

    await memory_store.close()

    try:
        await _checkpointer_cm.__aexit__(None, None, None)
    except Exception:
        LOGGER.exception("Failed to close checkpoint store")

    executor.shutdown(wait=False)
    LOGGER.info("Shutdown complete")


app = FastAPI(title="medical-agent", version="2.0.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ALLOW_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Register API routers (Agent chat + sessions)
# ---------------------------------------------------------------------------
from app.api.chat import router as chat_router  # noqa: E402
from app.api.sessions import router as sessions_router  # noqa: E402

app.include_router(chat_router)
app.include_router(sessions_router)


# ---------------------------------------------------------------------------
# Existing endpoints (task processing — unchanged)
# ---------------------------------------------------------------------------


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


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=os.getenv("UVICORN_HOST", "0.0.0.0"),
        port=read_int_env("UVICORN_PORT", 8090, 1),
        env_file=os.getenv("UVICORN_ENV_FILE", ".env"),
        reload=to_bool(os.getenv("UVICORN_RELOAD", "true")),
    )
