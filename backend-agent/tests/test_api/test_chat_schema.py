from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.schemas.chat import ChatRequest


def test_chat_request_accepts_typed_metadata_and_extra_fields() -> None:
    body = ChatRequest(
        message="请解读报告",
        metadata={
            "workflow": "report_interpretation",
            "scenario": "clinical_summary",
            "audience": "patient",
            "urgency_level": "high",
            "entry": "agent_page",
        },
    )

    metadata = body.metadata.to_runtime_metadata()

    assert metadata["workflow"] == "report_interpretation"
    assert metadata["entry"] == "agent_page"


def test_chat_request_rejects_unknown_workflow() -> None:
    with pytest.raises(ValidationError):
        ChatRequest(
            message="你好",
            metadata={"workflow": "unknown_workflow"},
        )


def test_chat_request_accepts_attachments() -> None:
    body = ChatRequest(
        message="看这份报告",
        attachments=[
            {
                "object_key": "records/report.pdf",
                "file_type": "PDF",
                "display_name": "门诊化验单",
            }
        ],
    )

    assert body.attachments[0].object_key == "records/report.pdf"
    assert body.attachments[0].file_type == "PDF"
