"""Short-term memory: LangGraph checkpoint persistence.

Wraps ``AsyncSqliteSaver`` from ``langgraph-checkpoint-sqlite`` to manage
the SQLite connection lifecycle.  Each conversation is isolated by
``thread_id``.
"""

from __future__ import annotations

import logging
import os
from typing import AsyncContextManager

from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver

from app.config import CHECKPOINT_DB_PATH

LOGGER = logging.getLogger(__name__)


async def create_checkpointer() -> tuple[
    AsyncSqliteSaver,
    AsyncContextManager[AsyncSqliteSaver],
]:
    """Create and initialise an ``AsyncSqliteSaver``.

    Returns a ``(saver, context_manager)`` tuple.  The caller must call
    ``await context_manager.__aexit__(None, None, None)`` on application
    shutdown to close the underlying ``aiosqlite`` connection.

    The parent directory for the database file is created automatically
    if it does not exist.
    """
    db_dir = os.path.dirname(CHECKPOINT_DB_PATH)
    if db_dir:
        os.makedirs(db_dir, exist_ok=True)

    conn_mgr = AsyncSqliteSaver.from_conn_string(CHECKPOINT_DB_PATH)
    saver = await conn_mgr.__aenter__()
    LOGGER.info("Checkpoint store opened: %s", CHECKPOINT_DB_PATH)
    return saver, conn_mgr
