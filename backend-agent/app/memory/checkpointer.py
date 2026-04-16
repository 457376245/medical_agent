"""短期记忆：LangGraph 检查点持久化。

封装 langgraph-checkpoint-sqlite 的 AsyncSqliteSaver 来管理 SQLite
连接生命周期。每个对话通过 thread_id 隔离。
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
    """创建并初始化 AsyncSqliteSaver。

    返回 (saver, context_manager) 元组。调用者必须在应用关闭时调用
    await context_manager.__aexit__(None, None, None) 来关闭底层的
    aiosqlite 连接。

    数据库文件的父目录如果不存在会自动创建。
    """
    db_dir = os.path.dirname(CHECKPOINT_DB_PATH)
    if db_dir:
        os.makedirs(db_dir, exist_ok=True)

    conn_mgr = AsyncSqliteSaver.from_conn_string(CHECKPOINT_DB_PATH)
    saver = await conn_mgr.__aenter__()
    LOGGER.info("Checkpoint store opened: %s", CHECKPOINT_DB_PATH)
    return saver, conn_mgr