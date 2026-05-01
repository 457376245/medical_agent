"""Identifier helpers."""

import uuid


def new_ordered_id() -> str:
    """Return a time-ordered UUID7 as a compact hex string."""
    return uuid.uuid7().hex


def new_prefixed_ordered_id(prefix: str, *, length: int = 32) -> str:
    return f"{prefix}-{new_ordered_id()[:length]}"
