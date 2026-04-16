"""Agent 状态定义。

定义 LangGraph StateGraph 使用的 AgentState TypedDict。
图中的所有节点读取和写入这个共享状态。
"""

from typing import Annotated, Any

from typing_extensions import NotRequired, TypedDict

from langgraph.graph.message import add_messages
from langchain_core.messages import BaseMessage


class AgentState(TypedDict):
    """Agent 图中流动的共享状态。

    Attributes:
        messages: 对话消息历史（通过 add_messages reducer 自动累积）。
        thread_id: 当前会话标识符。
        metadata: 任意请求元数据（patient_id、scenario 等）。
    """

    messages: Annotated[list[BaseMessage], add_messages]
    thread_id: str
    metadata: dict[str, Any]
    active_context_signature: NotRequired[str | None]
    active_context_bundle: NotRequired[dict[str, Any] | None]
    active_context_status: NotRequired[str | None]
    pending_context_signature: NotRequired[str | None]