"""Agent 会话存储。"""

from __future__ import annotations

import json
import logging
import os
from datetime import datetime, timezone
from typing import Any, Protocol, runtime_checkable

import aiosqlite

from app.config import MEMORY_DB_PATH
from app.ids import new_ordered_id
from app.memory.models import (
    AgentSessionRecord,
    AgentSessionTurn,
    AgentTraceEvent,
)

LOGGER = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# 协议（接口）
# ---------------------------------------------------------------------------


@runtime_checkable
class MemoryStore(Protocol):
    """Agent 会话存储接口。"""

    async def upsert_agent_session(self, session: AgentSessionRecord) -> None: ...

    async def get_agent_session(self, thread_id: str) -> AgentSessionRecord | None: ...

    async def list_agent_sessions(
        self,
        *,
        disease_profile_id: str | None = None,
        limit: int = 50,
    ) -> list[AgentSessionRecord]: ...

    async def save_agent_turn(self, turn: AgentSessionTurn) -> AgentSessionTurn: ...

    async def list_agent_turns(self, thread_id: str) -> list[AgentSessionTurn]: ...

    async def delete_agent_session(self, thread_id: str) -> None: ...

    async def update_agent_session_title(self, thread_id: str, title: str) -> None: ...

    async def close(self) -> None: ...


# ---------------------------------------------------------------------------
# SQLite 实现
# ---------------------------------------------------------------------------

_SCHEMA_SQL = """\
CREATE TABLE IF NOT EXISTS agent_sessions (
    thread_id              TEXT PRIMARY KEY,
    disease_profile_id     TEXT,
    disease_name           TEXT,
    record_id              TEXT,
    record_title           TEXT,
    record_date            TEXT,
    source_type            TEXT,
    context_signature      TEXT,
    context_status         TEXT,
    title                  TEXT,
    last_user_message      TEXT,
    last_assistant_message TEXT,
    last_message_preview   TEXT,
    created_at             TEXT NOT NULL,
    updated_at             TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_sessions_profile_updated
    ON agent_sessions (disease_profile_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS agent_session_turns (
    turn_id            TEXT PRIMARY KEY,
    thread_id          TEXT NOT NULL,
    turn_index         INTEGER NOT NULL,
    user_message       TEXT NOT NULL,
    assistant_message  TEXT NOT NULL DEFAULT '',
    trace_events       TEXT NOT NULL DEFAULT '[]',
    metadata           TEXT NOT NULL DEFAULT '{}',
    error_message      TEXT,
    created_at         TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_turn_thread_index
    ON agent_session_turns (thread_id, turn_index);

CREATE INDEX IF NOT EXISTS idx_agent_turn_thread_created
    ON agent_session_turns (thread_id, created_at ASC);
"""


class SqliteMemoryStore:
    """MemoryStore 的 SQLite 后端实现。"""

    def __init__(self, db_path: str | None = None) -> None:
        self._db_path = db_path or MEMORY_DB_PATH
        self._db: aiosqlite.Connection | None = None

    # -- 生命周期 -----------------------------------------------------------

    async def initialize(self) -> None:
        """打开数据库并在需要时创建表。"""
        db_dir = os.path.dirname(self._db_path)
        if db_dir:
            os.makedirs(db_dir, exist_ok=True)

        self._db = await aiosqlite.connect(self._db_path)
        await self._db.executescript(_SCHEMA_SQL)
        await self._ensure_agent_session_columns()
        await self._db.commit()
        LOGGER.info("Memory store opened: %s", self._db_path)

    async def close(self) -> None:
        if self._db is not None:
            await self._db.close()
            self._db = None
            LOGGER.info("Memory store closed")

    @property
    def _conn(self) -> aiosqlite.Connection:
        if self._db is None:
            raise RuntimeError(
                "SqliteMemoryStore 未初始化；请先调用 initialize()"
            )
        return self._db

    # -- Agent 会话 ---------------------------------------------------------

    async def upsert_agent_session(self, session: AgentSessionRecord) -> None:
        existing = await self.get_agent_session(session.thread_id)
        created_at = existing.created_at if existing is not None else session.created_at
        await self._conn.execute(
            "INSERT OR REPLACE INTO agent_sessions "
            "(thread_id, disease_profile_id, disease_name, record_id, "
            "record_title, record_date, source_type, context_signature, "
            "context_status, title, last_user_message, last_assistant_message, "
            "last_message_preview, created_at, updated_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                session.thread_id,
                session.disease_profile_id,
                session.disease_name,
                session.record_id,
                session.record_title,
                session.record_date,
                session.source_type,
                session.context_signature,
                session.context_status,
                session.title,
                session.last_user_message,
                session.last_assistant_message,
                session.last_message_preview,
                created_at.isoformat(),
                session.updated_at.isoformat(),
            ),
        )
        await self._conn.commit()

    async def get_agent_session(self, thread_id: str) -> AgentSessionRecord | None:
        cursor = await self._conn.execute(
            "SELECT thread_id, disease_profile_id, disease_name, record_id, "
            "record_title, record_date, source_type, context_signature, "
            "context_status, title, last_user_message, "
            "last_assistant_message, last_message_preview, created_at, updated_at "
            "FROM agent_sessions WHERE thread_id = ?",
            (thread_id,),
        )
        row = await cursor.fetchone()
        if row is None:
            return None
        turn_count = await self._count_agent_turns(thread_id)
        return AgentSessionRecord(
            thread_id=row[0],
            disease_profile_id=row[1],
            disease_name=row[2],
            record_id=row[3],
            record_title=row[4],
            record_date=row[5],
            source_type=row[6],
            context_signature=row[7],
            context_status=row[8],
            title=row[9],
            last_user_message=row[10],
            last_assistant_message=row[11],
            last_message_preview=row[12],
            created_at=datetime.fromisoformat(row[13]),
            updated_at=datetime.fromisoformat(row[14]),
            turn_count=turn_count,
        )

    async def list_agent_sessions(
        self,
        *,
        disease_profile_id: str | None = None,
        limit: int = 50,
    ) -> list[AgentSessionRecord]:
        clauses: list[str] = []
        params: list[Any] = []
        if disease_profile_id is not None:
            clauses.append("s.disease_profile_id = ?")
            params.append(disease_profile_id)

        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        query = (
            "SELECT s.thread_id, s.disease_profile_id, s.disease_name, s.record_id, "
            "s.record_title, s.record_date, s.source_type, s.context_signature, "
            "s.context_status, s.title, "
            "s.last_user_message, s.last_assistant_message, s.last_message_preview, "
            "s.created_at, s.updated_at, COALESCE(t.turn_count, 0) "
            "FROM agent_sessions s "
            "LEFT JOIN ("
            "  SELECT thread_id, COUNT(*) AS turn_count "
            "  FROM agent_session_turns "
            "  GROUP BY thread_id"
            ") t ON t.thread_id = s.thread_id "
            f"{where} "
            "ORDER BY s.updated_at DESC "
            "LIMIT ?"
        )
        params.append(limit)
        cursor = await self._conn.execute(query, params)
        rows = await cursor.fetchall()
        return [
            AgentSessionRecord(
                thread_id=row[0],
                disease_profile_id=row[1],
                disease_name=row[2],
                record_id=row[3],
                record_title=row[4],
                record_date=row[5],
                source_type=row[6],
                context_signature=row[7],
                context_status=row[8],
                title=row[9],
                last_user_message=row[10],
                last_assistant_message=row[11],
                last_message_preview=row[12],
                created_at=datetime.fromisoformat(row[13]),
                updated_at=datetime.fromisoformat(row[14]),
                turn_count=int(row[15]),
            )
            for row in rows
        ]

    async def save_agent_turn(self, turn: AgentSessionTurn) -> AgentSessionTurn:
        turn_id = turn.turn_id or new_ordered_id()
        await self._conn.execute(
            "INSERT OR REPLACE INTO agent_session_turns "
            "(turn_id, thread_id, turn_index, user_message, assistant_message, "
            "trace_events, metadata, error_message, created_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                turn_id,
                turn.thread_id,
                turn.turn_index,
                turn.user_message,
                turn.assistant_message,
                json.dumps(
                    [event.model_dump(mode="json") for event in turn.trace_events],
                    ensure_ascii=False,
                ),
                json.dumps(turn.metadata, ensure_ascii=False),
                turn.error_message,
                turn.created_at.isoformat(),
            ),
        )
        await self._conn.commit()
        return AgentSessionTurn(
            turn_id=turn_id,
            thread_id=turn.thread_id,
            turn_index=turn.turn_index,
            user_message=turn.user_message,
            assistant_message=turn.assistant_message,
            trace_events=turn.trace_events,
            metadata=turn.metadata,
            error_message=turn.error_message,
            created_at=turn.created_at,
        )

    async def list_agent_turns(self, thread_id: str) -> list[AgentSessionTurn]:
        cursor = await self._conn.execute(
            "SELECT turn_id, thread_id, turn_index, user_message, assistant_message, "
            "trace_events, metadata, error_message, created_at "
            "FROM agent_session_turns WHERE thread_id = ? "
            "ORDER BY turn_index ASC, created_at ASC",
            (thread_id,),
        )
        rows = await cursor.fetchall()
        turns: list[AgentSessionTurn] = []
        for row in rows:
            raw_trace = json.loads(row[5])
            turns.append(
                AgentSessionTurn(
                    turn_id=row[0],
                    thread_id=row[1],
                    turn_index=int(row[2]),
                    user_message=row[3],
                    assistant_message=row[4],
                    trace_events=[
                        AgentTraceEvent(
                            event=item.get("event", "error"),
                            tool=item.get("tool"),
                            data=item.get("data", {}),
                            created_at=datetime.fromisoformat(item["created_at"])
                            if item.get("created_at")
                            else datetime.now(timezone.utc),
                        )
                        for item in raw_trace
                    ],
                    metadata=json.loads(row[6]),
                    error_message=row[7],
                    created_at=datetime.fromisoformat(row[8]),
                )
            )
        return turns

    async def delete_agent_session(self, thread_id: str) -> None:
        await self._conn.execute(
            "DELETE FROM agent_session_turns WHERE thread_id = ?",
            (thread_id,),
        )
        await self._conn.execute(
            "DELETE FROM agent_sessions WHERE thread_id = ?",
            (thread_id,),
        )
        await self._conn.commit()

    async def update_agent_session_title(self, thread_id: str, title: str) -> None:
        await self._conn.execute(
            "UPDATE agent_sessions SET title = ?, updated_at = ? WHERE thread_id = ?",
            (title, datetime.now(timezone.utc).isoformat(), thread_id),
        )
        await self._conn.commit()

    async def _count_agent_turns(self, thread_id: str) -> int:
        cursor = await self._conn.execute(
            "SELECT COUNT(*) FROM agent_session_turns WHERE thread_id = ?",
            (thread_id,),
        )
        row = await cursor.fetchone()
        return int(row[0] if row is not None else 0)

    async def _ensure_agent_session_columns(self) -> None:
        cursor = await self._conn.execute("PRAGMA table_info(agent_sessions)")
        rows = await cursor.fetchall()
        columns = {str(row[1]) for row in rows}
        if "context_signature" not in columns:
            await self._conn.execute(
                "ALTER TABLE agent_sessions ADD COLUMN context_signature TEXT"
            )
        if "context_status" not in columns:
            await self._conn.execute(
                "ALTER TABLE agent_sessions ADD COLUMN context_status TEXT"
            )
