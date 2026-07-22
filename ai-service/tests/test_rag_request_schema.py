from app.schemas import RagQueryRequest


def test_null_drafting_information_from_legacy_backend_is_normalized() -> None:
    request = RagQueryRequest.model_validate(
        {
            "request_id": "req-1",
            "user_id": "1",
            "workspace_id": "ws-1",
            "question": "Bảo hiểm tai nạn lao động là gì?",
            "drafting_information": None,
        }
    )

    assert request.draftingInformation == {}
