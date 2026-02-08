import asyncio
import json
import logging
import os
from collections.abc import Awaitable, Callable
from typing import Any

import aio_pika


LOGGER = logging.getLogger(__name__)


class AgentMqConsumer:
    def __init__(
        self,
        parse_handler: Callable[[dict[str, Any]], Awaitable[dict[str, Any]]],
        generate_handler: Callable[[dict[str, Any]], Awaitable[dict[str, Any]]],
    ) -> None:
        self._parse_handler = parse_handler
        self._generate_handler = generate_handler
        self._connection: aio_pika.abc.AbstractRobustConnection | None = None
        self._channel: aio_pika.abc.AbstractChannel | None = None
        self._closing = False

    async def start(self) -> None:
        rabbitmq_url = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
        self._connection = await aio_pika.connect_robust(rabbitmq_url)
        connection = self._connection
        self._channel = await connection.channel()
        await self._channel.set_qos(prefetch_count=5)

        exchange = await self._channel.declare_exchange(
            "agent.exchange.v1", aio_pika.ExchangeType.DIRECT, durable=True
        )

        parse_request_q = await self._channel.declare_queue(
            "agent.parse.request.v1", durable=True
        )
        generate_request_q = await self._channel.declare_queue(
            "agent.generate.request.v1", durable=True
        )

        await parse_request_q.bind(exchange, routing_key="agent.parse.request.v1")
        await generate_request_q.bind(exchange, routing_key="agent.generate.request.v1")

        await parse_request_q.consume(
            lambda message: self._handle_parse(message, exchange), no_ack=False
        )
        await generate_request_q.consume(
            lambda message: self._handle_generate(message, exchange), no_ack=False
        )
        LOGGER.info("Agent MQ consumers started")

    async def close(self) -> None:
        self._closing = True
        if self._channel is not None:
            await self._channel.close()
        if self._connection is not None:
            await self._connection.close()

    async def _handle_parse(
        self,
        message: aio_pika.abc.AbstractIncomingMessage,
        exchange: aio_pika.abc.AbstractExchange,
    ) -> None:
        async with message.process(requeue=False):
            payload = json.loads(message.body.decode("utf-8"))
            LOGGER.info("MQ parse request received: jobId=%s", payload.get("jobId"))
            result = await self._parse_handler(payload)
            event = {
                "jobId": payload.get("jobId"),
                "status": result.get("status", "FAILED"),
                "structuredResult": result.get("structuredResult", {}),
                "confidence": result.get("confidence", 0.0),
                "errors": result.get("errors", []),
                "traceId": payload.get("traceId", ""),
                "schemaVersion": payload.get("schemaVersion", "v1"),
            }
            await exchange.publish(
                aio_pika.Message(
                    body=json.dumps(event).encode("utf-8"),
                    content_type="application/json",
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                ),
                routing_key="agent.parse.result.v1",
            )
            LOGGER.info(
                "MQ parse result published: jobId=%s status=%s error_codes=%s",
                payload.get("jobId"),
                event.get("status"),
                [
                    item.get("code")
                    for item in event.get("errors", [])
                    if isinstance(item, dict)
                ],
            )

    async def _handle_generate(
        self,
        message: aio_pika.abc.AbstractIncomingMessage,
        exchange: aio_pika.abc.AbstractExchange,
    ) -> None:
        async with message.process(requeue=False):
            payload = json.loads(message.body.decode("utf-8"))
            LOGGER.info(
                "MQ generate request received: taskId=%s", payload.get("taskId")
            )
            result = await self._generate_handler(payload)
            event = {
                "taskId": payload.get("taskId"),
                "recordId": payload.get("recordId"),
                "status": result.get("status", "FAILED"),
                "type": result.get("type", payload.get("type", "SUMMARY")),
                "content": result.get("content", ""),
                "modelMeta": result.get("modelMeta", {}),
                "errors": result.get("errors", []),
                "traceId": payload.get("traceId", ""),
            }
            await exchange.publish(
                aio_pika.Message(
                    body=json.dumps(event).encode("utf-8"),
                    content_type="application/json",
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                ),
                routing_key="agent.generate.result.v1",
            )
            LOGGER.info(
                "MQ generate result published: taskId=%s status=%s error_codes=%s",
                payload.get("taskId"),
                event.get("status"),
                [
                    item.get("code")
                    for item in event.get("errors", [])
                    if isinstance(item, dict)
                ],
            )


async def wait_forever() -> None:
    while True:
        await asyncio.sleep(60)
