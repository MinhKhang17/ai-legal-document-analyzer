from app.models.intent_enums import ContractType
from app.services.intent_detector import detect_contract_type
from app.services.prompt_builder import build_system_prompt
from fastapi.testclient import TestClient
from app.main import app
from app.models.knowledge_models import RetrievedChunk
from app.services.retrieval_service import RetrievalService


def test_detector_uses_canonical_granular_types() -> None:
    assert detect_contract_type("hợp đồng thực tập của tôi") == ContractType.INTERNSHIP
    assert detect_contract_type("hợp đồng cộng tác viên") == ContractType.COLLABORATOR
    assert detect_contract_type("hợp đồng làm thêm part-time") == ContractType.PART_TIME_EMPLOYMENT
    assert detect_contract_type("bán laptop cá nhân") == ContractType.SMALL_ASSET_SALE


def test_complex_contract_is_internal_unsupported_fallback() -> None:
    assert detect_contract_type("hợp đồng M&A xuyên biên giới") == ContractType.UNSUPPORTED


def test_system_prompt_states_scope_and_lawyer_limitation() -> None:
    prompt = build_system_prompt()
    assert "không phải luật sư" in prompt
    assert "hợp đồng thương mại phức tạp" in prompt
    assert "mua bán tài sản cá nhân nhỏ" in prompt


def test_direct_analysis_rejects_unsupported_type_before_processing() -> None:
    response = TestClient(app).post(
        "/v2/contracts/upload",
        data={"contract_type": "UNSUPPORTED"},
        files={"file": ("hop-dong.pdf", b"%PDF-1.4", "application/pdf")},
    )
    assert response.status_code == 422
    assert response.json()["detail"]["code"] == "UNSUPPORTED_CONTRACT_TYPE"


def test_retrieval_filters_explicitly_unsupported_or_unconfirmed_vectors() -> None:
    unsupported = RetrievedChunk("1", "text", 1.0, "title", metadata={"contract_type": "UNSUPPORTED", "contract_type_confirmed": True})
    unconfirmed = RetrievedChunk("2", "text", 1.0, "title", metadata={"contract_type": "RENTAL", "contract_type_confirmed": False})
    supported = RetrievedChunk("3", "text", 1.0, "title", metadata={"contract_type": "RENTAL", "contract_type_confirmed": True})
    assert not RetrievalService._is_supported_user_contract_chunk(unsupported)
    assert not RetrievalService._is_supported_user_contract_chunk(unconfirmed)
    assert RetrievalService._is_supported_user_contract_chunk(supported)
