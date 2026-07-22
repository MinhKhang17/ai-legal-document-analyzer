from app.models.intent_enums import ContractType
from app.services.intent_detector import detect_contract_type
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
