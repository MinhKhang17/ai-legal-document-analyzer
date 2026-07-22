from app.schemas import RagQueryRequest
from app.services.safe_query_shortcuts import (
    build_contract_prompt_response,
    build_conversation_shortcut,
    redact_sensitive_text,
)


def request(question: str) -> RagQueryRequest:
    return RagQueryRequest(
        request_id="req_test",
        user_id="1",
        workspace_id="ws_1",
        question=question,
    )


def test_capability_question_is_answered_without_document_retrieval() -> None:
    response = build_conversation_shortcut(request("Hệ thống hỗ trợ chức năng gì?"))

    assert response is not None
    assert response.intent == "CAPABILITY_QUESTION"
    assert response.retrievedUserChunks == 0
    assert response.retrievedKnowledgeChunks == 0
    assert "chưa đính kèm tài liệu" in response.answer


def test_contract_creation_without_type_requests_contract_type_and_no_prompt() -> None:
    response = build_contract_prompt_response(
        request("Soạn hợp đồng cho email an.nguyen@example.com, số điện thoại 0912345678, giá 12.000.000 đồng")
    )

    assert response.intent == "DRAFT_CONTRACT"
    assert response.draftingStatus == "NEED_CONTRACT_TYPE"
    assert response.suggestedActions == ["SELECT_CONTRACT_TYPE"]
    assert response.draftingPrompt is None
    assert not hasattr(response, "downloadUrl")


def test_redaction_is_consistent_for_repeated_values() -> None:
    result, changed = redact_sensitive_text("Gọi 0912345678 hoặc 0912345678")

    assert changed is True
    assert result == "Gọi [PHONE_1] hoặc [PHONE_1]"
