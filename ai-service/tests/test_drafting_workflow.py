import pytest

from app.schemas import ConversationMessage, RagQueryRequest
from app.services.drafting_workflow import build_drafting_response


def request(**values) -> RagQueryRequest:
    return RagQueryRequest(
        request_id="req-drafting",
        user_id="1",
        workspace_id="ws-1",
        question=values.pop("question", "Soạn hợp đồng"),
        **values,
    )


def test_missing_contract_type_never_generates_prompt() -> None:
    response = build_drafting_response(request())
    assert response.draftingStatus == "NEED_CONTRACT_TYPE"
    assert response.questions == []
    assert response.draftingPrompt is None


@pytest.mark.parametrize(
    ("contract_type", "expected_question", "expected_structure"),
    [
        ("HOUSE_RENTAL_CONTRACT", "property_address", "Bàn giao nhà"),
        ("LOAN_CONTRACT", "principal", "Lãi suất và cách tính"),
        ("SALE_OF_GOODS_CONTRACT", "goods", "Chuyển quyền sở hữu và rủi ro"),
        ("EMPLOYMENT_CONTRACT", "salary", "Bảo hiểm và quyền lợi"),
        ("SERVICE_CONTRACT", "scope", "Phạm vi dịch vụ"),
        ("OTHER", "contract_name", "Mục đích và phạm vi"),
    ],
)
def test_contract_types_ask_specific_questions_and_use_specific_structure(
    contract_type: str, expected_question: str, expected_structure: str,
) -> None:
    questions = build_drafting_response(request(
        drafting_action="SELECT_CONTRACT_TYPE",
        drafting_contract_type=contract_type,
    ))
    assert questions.draftingStatus == "NEED_MORE_INFORMATION"
    assert expected_question in {question.key for question in questions.questions}
    assert questions.draftingPrompt is None

    generated = build_drafting_response(request(
        drafting_action="CONTINUE_WITH_PLACEHOLDERS",
        drafting_contract_type=contract_type,
        drafting_original_requirement="Tạo bản nháp để tôi rà soát",
    ))
    assert generated.draftingStatus == "PROMPT_GENERATED"
    assert expected_structure in generated.draftingPrompt
    assert "Nội dung cần luật sư kiểm tra" in generated.draftingPrompt


def test_provided_values_are_not_asked_again_and_missing_values_use_consistent_placeholders() -> None:
    response = build_drafting_response(request(
        drafting_action="GENERATE_PROMPT",
        drafting_contract_type="HOUSE_RENTAL_CONTRACT",
        drafting_information={"monthly_rent": "5 triệu đồng", "deposit": ""},
        drafting_original_requirement="Soạn hợp đồng thuê nhà",
    ))
    assert response.providedInformation == {"monthly_rent": "5 triệu đồng"}
    assert "Giá thuê hàng tháng" not in response.draftingMissingInformation
    assert response.draftingPrompt.count("[TIỀN_ĐẶT_CỌC]") >= 2


def test_personal_information_is_redacted_before_external_prompt() -> None:
    response = build_drafting_response(request(
        drafting_action="GENERATE_PROMPT",
        drafting_contract_type="LOAN_CONTRACT",
        drafting_information={
            "lender": "an.nguyen@example.com - 0912345678",
            "borrower": "Liên hệ 0912345678",
        },
        drafting_original_requirement="Soạn giấy vay cho an.nguyen@example.com",
    ))
    assert "an.nguyen@example.com" not in response.draftingPrompt
    assert "0912345678" not in response.draftingPrompt
    assert "[BÊN_CHO_VAY]" in response.draftingPrompt
    assert "[BÊN_VAY]" in response.draftingPrompt
    assert response.redactionRequired is True
    assert response.llmExecuted is False

    unstructured = build_drafting_response(request(
        drafting_action="GENERATE_PROMPT",
        drafting_contract_type="SERVICE_CONTRACT",
        drafting_original_requirement="Soạn cho ông Nguyễn Văn A, địa chỉ: 12 Nguyễn Trãi",
    ))
    assert "Nguyễn Văn A" not in unstructured.draftingPrompt
    assert "12 Nguyễn Trãi" not in unstructured.draftingPrompt


def test_existing_conversation_information_is_not_asked_again() -> None:
    response = build_drafting_response(request(
        question="Soạn hợp đồng thuê nhà",
        drafting_action="SELECT_CONTRACT_TYPE",
        drafting_contract_type="HOUSE_RENTAL_CONTRACT",
        recent_history=[ConversationMessage(
            messageId="msg-1",
            role="USER",
            content="Giá thuê: 5 triệu đồng; tiền đặt cọc: 10 triệu đồng; thời hạn thuê: 12 tháng",
        )],
    ))
    assert response.providedInformation["monthly_rent"] == "5 triệu đồng"
    assert response.providedInformation["deposit"] == "10 triệu đồng"
    assert response.providedInformation["rental_term"] == "12 tháng"
    remaining = {question.key for question in response.questions}
    assert "monthly_rent" not in remaining
    assert "deposit" not in remaining
    assert "rental_term" not in remaining
