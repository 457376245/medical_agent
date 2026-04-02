"""Agent state definition.

Defines the ``AgentState`` TypedDict consumed by the LangGraph StateGraph.
All nodes in the graph read from and write to this shared state.
"""

from typing import Annotated, Any

from typing_extensions import NotRequired, TypedDict

from langgraph.graph.message import add_messages
from langchain_core.messages import BaseMessage


class AgentState(TypedDict):
    """Shared state flowing through the Agent graph.

    Attributes:
        messages: Conversation message history (auto-accumulated via
                  ``add_messages`` reducer).
        thread_id: Current session identifier.
        metadata: Arbitrary per-request metadata (patient_id, scenario, …).
    """

    messages: Annotated[list[BaseMessage], add_messages]
    thread_id: str
    metadata: dict[str, Any]
    active_context_signature: NotRequired[str | None]
    active_context_bundle: NotRequired[dict[str, Any] | None]
    active_context_status: NotRequired[str | None]
    pending_context_signature: NotRequired[str | None]
