from app.services.patient_memory import _extract_chat_content, _normalize_entry


def test_normalize_entry_rejects_unsupported_field_path() -> None:
    assert _normalize_entry({"fieldPath": "unknown", "valueText": "test"}) is None


def test_normalize_entry_marks_medication_as_high_risk() -> None:
    entry = _normalize_entry(
        {
            "memoryType": "MEDICATION",
            "fieldPath": "currentMedications",
            "valueText": "二甲双胍",
            "value": None,
            "evidenceText": "用户说一直吃二甲双胍",
            "confidence": 0.9,
            "riskLevel": "",
        }
    )

    assert entry is not None
    assert entry["fieldPath"] == "currentMedications"
    assert entry["riskLevel"] == "HIGH"
    assert entry["confidence"] == 0.9


def test_extract_chat_content_supports_streaming_response() -> None:
    raw_body = "\n".join(
        [
            'data: {"choices":[{"delta":{"content":"{\\"entries\\":["}}]}',
            'data: {"choices":[{"delta":{"content":"]}"}}]}',
            "data: [DONE]",
        ]
    )

    assert _extract_chat_content(raw_body) == '{"entries":[]}'


def test_normalize_entry_accepts_personal_context() -> None:
    entry = _normalize_entry(
        {
            "memoryType": "",
            "fieldPath": "personalContext",
            "valueText": "家属协助记录血压",
            "value": None,
            "evidenceText": "用户说平时由女儿帮忙记录血压",
            "confidence": 0.7,
            "riskLevel": "",
        }
    )

    assert entry is not None
    assert entry["memoryType"] == "PERSONAL_CONTEXT"
    assert entry["riskLevel"] == "LOW"
