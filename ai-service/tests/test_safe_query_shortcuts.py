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


def test_contract_creation_returns_anonymized_prompt_and_no_file() -> None:
    response = build_contract_prompt_response(
        request("Soạn hợp đồng cho email an.nguyen@example.com, số điện thoại 0912345678, giá 12.000.000 đồng")
    )

    assert response.intent == "CONTRACT_PROMPT_GENERATION"
    assert response.suggestedActions == ["COPY_PROMPT", "OPEN_CHATGPT"]
    assert response.draftingPrompt is not None
    assert "an.nguyen@example.com" not in response.draftingPrompt
    assert "0912345678" not in response.draftingPrompt
    assert "[EMAIL_1]" in response.draftingPrompt
    assert "[PHONE_1]" in response.draftingPrompt
    assert not hasattr(response, "downloadUrl")


def test_redaction_is_consistent_for_repeated_values() -> None:
    result, changed = redact_sensitive_text("Gọi 0912345678 hoặc 0912345678")

    assert changed is True
    assert result == "Gọi [PHONE_1] hoặc [PHONE_1]"
