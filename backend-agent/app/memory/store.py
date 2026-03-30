"""Long-term memory: cross-session knowledge store.

Defines the ``MemoryStore`` protocol and provides a SQLite implementation.
Stores patient context, conversation summaries, and extracted medical facts
that persist across sessions.
"""

from __future__ import annotations

import json
import logging
import os
import uuid
from datetime import datetime
from typing import Any, Protocol, runtime_checkable

import aiosqlite

from app.config import MEMORY_DB_PATH
from app.memory.models import ConversationSummary, MedicalFact, PatientContext

LOGGER = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Protocol (interface)
# ---------------------------------------------------------------------------


@runtime_checkable
class MemoryStore(Protocol):
    """Abstract interface for long-term memory storage.

    Implementations may use SQLite, PostgreSQL, Redis, or any other
    backend.  The protocol is intentionally narrow so that swapping
    backends requires minimal effort.
    """

    async def save_summary(self, summary: ConversationSummary) -> None: ...

    async def get_summary(self, thread_id: str) -> ConversationSummary | None: ...

    async def save_patient_context(self, ctx: PatientContext) -> None: ...

    async def get_patient_context(self, patient_id: str) -> PatientContext | None: ...

    async def save_fact(self, fact: MedicalFact) -> None: ...

    async def get_facts(
        self,
        *,
        thread_id: str | None = None,
        patient_id: str | None = None,
        category: str | None = None,
        limit: int = 50,
    ) -> list[MedicalFact]: ...

    async def close(self) -> None: ...


# ---------------------------------------------------------------------------
# SQLite implementation
# ---------------------------------------------------------------------------

_SCHEMA_SQL = """\
CREATE TABLE IF NOT EXISTS conversation_summaries (
    thread_id   TEXT PRIMARY KEY,
    summary     TEXT    NOT NULL,
    key_topics  TEXT    NOT NULL DEFAULT '[]',
    created_at  TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS patient_contexts (
    patient_id   TEXT PRIMARY KEY,
    thread_id    TEXT    NOT NULL,
    demographics TEXT    NOT NULL DEFAULT '{}',
    diagnoses    TEXT    NOT NULL DEFAULT '[]',
    medications  TEXT    NOT NULL DEFAULT '[]',
    allergies    TEXT    NOT NULL DEFAULT '[]',
    updated_at   TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS medical_facts (
    fact_id     TEXT PRIMARY KEY,
    thread_id   TEXT    NOT NULL,
    patient_id  TEXT,
    category    TEXT    NOT NULL,
    content     TEXT    NOT NULL,
    source      TEXT,
    confidence  REAL    NOT NULL DEFAULT 1.0,
    created_at  TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_facts_thread
    ON medical_facts (thread_id);
CREATE INDEX IF NOT EXISTS idx_facts_patient
    ON medical_facts (patient_id);
CREATE INDEX IF NOT EXISTS idx_facts_category
    ON medical_facts (category);
"""


class SqliteMemoryStore:
    """SQLite-backed implementation of :class:`MemoryStore`."""

    def __init__(self, db_path: str | None = None) -> None:
        self._db_path = db_path or MEMORY_DB_PATH
        self._db: aiosqlite.Connection | None = None

    # -- lifecycle -----------------------------------------------------------

    async def initialize(self) -> None:
        """Open the database and create tables if needed."""
        db_dir = os.path.dirname(self._db_path)
        if db_dir:
            os.makedirs(db_dir, exist_ok=True)

        self._db = await aiosqlite.connect(self._db_path)
        await self._db.executescript(_SCHEMA_SQL)
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
                "SqliteMemoryStore is not initialised; call initialize() first"
            )
        return self._db

    # -- summaries -----------------------------------------------------------

    async def save_summary(self, summary: ConversationSummary) -> None:
        await self._conn.execute(
            "INSERT OR REPLACE INTO conversation_summaries "
            "(thread_id, summary, key_topics, created_at) VALUES (?, ?, ?, ?)",
            (
                summary.thread_id,
                summary.summary,
                json.dumps(summary.key_topics, ensure_ascii=False),
                summary.created_at.isoformat(),
            ),
        )
        await self._conn.commit()

    async def get_summary(self, thread_id: str) -> ConversationSummary | None:
        cursor = await self._conn.execute(
            "SELECT thread_id, summary, key_topics, created_at "
            "FROM conversation_summaries WHERE thread_id = ?",
            (thread_id,),
        )
        row = await cursor.fetchone()
        if row is None:
            return None
        return ConversationSummary(
            thread_id=row[0],
            summary=row[1],
            key_topics=json.loads(row[2]),
            created_at=datetime.fromisoformat(row[3]),
        )

    # -- patient context -----------------------------------------------------

    async def save_patient_context(self, ctx: PatientContext) -> None:
        await self._conn.execute(
            "INSERT OR REPLACE INTO patient_contexts "
            "(patient_id, thread_id, demographics, diagnoses, "
            "medications, allergies, updated_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            (
                ctx.patient_id,
                ctx.thread_id,
                json.dumps(ctx.demographics, ensure_ascii=False),
                json.dumps(ctx.diagnoses, ensure_ascii=False),
                json.dumps(ctx.medications, ensure_ascii=False),
                json.dumps(ctx.allergies, ensure_ascii=False),
                ctx.updated_at.isoformat(),
            ),
        )
        await self._conn.commit()

    async def get_patient_context(self, patient_id: str) -> PatientContext | None:
        cursor = await self._conn.execute(
            "SELECT patient_id, thread_id, demographics, diagnoses, "
            "medications, allergies, updated_at "
            "FROM patient_contexts WHERE patient_id = ?",
            (patient_id,),
        )
        row = await cursor.fetchone()
        if row is None:
            return None
        return PatientContext(
            patient_id=row[0],
            thread_id=row[1],
            demographics=json.loads(row[2]),
            diagnoses=json.loads(row[3]),
            medications=json.loads(row[4]),
            allergies=json.loads(row[5]),
            updated_at=datetime.fromisoformat(row[6]),
        )

    # -- medical facts -------------------------------------------------------

    async def save_fact(self, fact: MedicalFact) -> None:
        fact_id = fact.fact_id or uuid.uuid4().hex
        await self._conn.execute(
            "INSERT OR REPLACE INTO medical_facts "
            "(fact_id, thread_id, patient_id, category, content, "
            "source, confidence, created_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (
                fact_id,
                fact.thread_id,
                fact.patient_id,
                fact.category,
                fact.content,
                fact.source,
                fact.confidence,
                fact.created_at.isoformat(),
            ),
        )
        await self._conn.commit()

    async def get_facts(
        self,
        *,
        thread_id: str | None = None,
        patient_id: str | None = None,
        category: str | None = None,
        limit: int = 50,
    ) -> list[MedicalFact]:
        clauses: list[str] = []
        params: list[Any] = []
        if thread_id is not None:
            clauses.append("thread_id = ?")
            params.append(thread_id)
        if patient_id is not None:
            clauses.append("patient_id = ?")
            params.append(patient_id)
        if category is not None:
            clauses.append("category = ?")
            params.append(category)

        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        query = (
            f"SELECT fact_id, thread_id, patient_id, category, content, "
            f"source, confidence, created_at "
            f"FROM medical_facts {where} "
            f"ORDER BY created_at DESC LIMIT ?"
        )
        params.append(limit)

        cursor = await self._conn.execute(query, params)
        rows = await cursor.fetchall()
        return [
            MedicalFact(
                fact_id=r[0],
                thread_id=r[1],
                patient_id=r[2],
                category=r[3],
                content=r[4],
                source=r[5],
                confidence=r[6],
                created_at=datetime.fromisoformat(r[7]),
            )
            for r in rows
        ]
