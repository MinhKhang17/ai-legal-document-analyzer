from app.models.intent_enums import ContractType, LegalQueryIntent
from app.services.intent_detector import detect_contract_type, detect_intent
from app.services.conversation_context import is_follow_up_query
from app.services.prompt_builder import build_system_prompt
from app.schemas import DocumentProcessRequest


def test_detector_uses_canonical_granular_types() -> None:
    assert detect_contract_type("hợp đồng thực tập của tôi") == ContractType.INTERNSHIP
    assert detect_contract_type("hợp đồng cộng tác viên") == ContractType.COLLABORATOR
    assert detect_contract_type("hợp đồng làm thêm part-time") == ContractType.PART_TIME_EMPLOYMENT
    assert detect_contract_type("hợp đồng lao động của tôi") == ContractType.PART_TIME_EMPLOYMENT
    assert detect_contract_type("bán laptop cá nhân") == ContractType.SMALL_ASSET_SALE


def test_complex_contract_is_internal_unsupported_fallback() -> None:
    assert detect_contract_type("hợp đồng M&A xuyên biên giới") == ContractType.UNSUPPORTED
    assert detect_contract_type("hợp đồng bảo hiểm nhân thọ") == ContractType.UNSUPPORTED


def test_employment_insurance_follow_up_stays_in_legal_qa_scope() -> None:
    question = "Bảo hiểm tai nạn lao động bạn nhắc đến là gì và mức bao nhiêu?"

    assert is_follow_up_query(question)
    assert detect_contract_type(question) == ContractType.UNKNOWN
    result = detect_intent(
        question,
        has_knowledge_chunks=True,
        conversation_context="Trước đó đang rà soát hợp đồng lao động của người dùng.",
    )
    assert result.intent == LegalQueryIntent.LEGAL_KB_QUESTION


def test_system_prompt_states_scope_and_lawyer_limitation() -> None:
    prompt = build_system_prompt()
    assert "không phải luật sư" in prompt
    assert "hợp đồng thương mại phức tạp" in prompt
    assert "mua bán tài sản cá nhân nhỏ" in prompt


def test_document_processing_request_does_not_require_contract_type() -> None:
    request = DocumentProcessRequest(
        jobId="job-1",
        documentId="doc-1",
        workspaceId="ws-1",
        userId="user-1",
        sourceType="USER_DOCUMENT",
        fileName="contract.pdf",
        fileType="pdf",
        filePath="/tmp/contract.pdf",
        callbackUrl="http://backend/callback",
    )
    assert request.contractType is None
    assert request.contractTypeConfirmed is None
