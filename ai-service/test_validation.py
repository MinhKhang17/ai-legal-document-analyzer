"""Test validation của các models sau khi nâng cấp."""
from app.models.legal_models import (
    AnalyzeContractRequest,
    ClassifyContractRequest,
    CompareContractsRequest,
    ContractType,
)
from app.models.rag_models import RagRetrieveRequest, ContractContextRequest
from pydantic import ValidationError


def test_classify_request_validation():
    """Test ClassifyContractRequest validation."""
    print("Testing ClassifyContractRequest...")
    
    # Valid request
    valid_req = ClassifyContractRequest(
        text="HỢP ĐỒNG THUÊ NHÀ. Bên cho thuê: Nguyễn Văn A. Bên thuê: Trần Thị B."
    )
    print(f"  ✓ Valid request: text length = {len(valid_req.text)}")
    
    # Too short
    try:
        ClassifyContractRequest(text="short")
        print("  ✗ FAIL: Should reject text < 10 chars")
    except ValidationError:
        print("  ✓ Correctly rejects text < 10 chars")
    
    # Too long
    try:
        ClassifyContractRequest(text="x" * 100001)
        print("  ✗ FAIL: Should reject text > 100000 chars")
    except ValidationError:
        print("  ✓ Correctly rejects text > 100000 chars")


def test_analyze_request_validation():
    """Test AnalyzeContractRequest validation."""
    print("\nTesting AnalyzeContractRequest...")
    
    # Valid request
    valid_req = AnalyzeContractRequest(
        contractText="HỢP ĐỒNG THUÊ NHÀ. Bên cho thuê: Nguyễn Văn A.",
        contractType=ContractType.HOUSE_RENTAL,
        protectedParty="bên thuê",
        question="Hợp đồng này có rủi ro gì?",
    )
    print(f"  ✓ Valid request created")
    
    # protectedParty too long
    try:
        AnalyzeContractRequest(
            contractText="Test contract text here.",
            protectedParty="x" * 201,
        )
        print("  ✗ FAIL: Should reject protectedParty > 200 chars")
    except ValidationError:
        print("  ✓ Correctly rejects protectedParty > 200 chars")
    
    # question too long
    try:
        AnalyzeContractRequest(
            contractText="Test contract text here.",
            question="x" * 501,
        )
        print("  ✗ FAIL: Should reject question > 500 chars")
    except ValidationError:
        print("  ✓ Correctly rejects question > 500 chars")


def test_compare_request_validation():
    """Test CompareContractsRequest validation."""
    print("\nTesting CompareContractsRequest...")
    
    # Valid request
    valid_req = CompareContractsRequest(
        documentAText="HỢP ĐỒNG VERSION 1. Nội dung hợp đồng...",
        documentBText="HỢP ĐỒNG VERSION 2. Nội dung hợp đồng...",
    )
    print(f"  ✓ Valid request created")
    
    # documentA too short
    try:
        CompareContractsRequest(
            documentAText="short",
            documentBText="HỢP ĐỒNG VERSION 2. Nội dung...",
        )
        print("  ✗ FAIL: Should reject documentAText < 10 chars")
    except ValidationError:
        print("  ✓ Correctly rejects documentAText < 10 chars")


def test_rag_retrieve_validation():
    """Test RagRetrieveRequest validation."""
    print("\nTesting RagRetrieveRequest...")
    
    # Valid request
    valid_req = RagRetrieveRequest(
        query="rủi ro đặt cọc trong hợp đồng",
        topK=10,
    )
    print(f"  ✓ Valid request: query = '{valid_req.query}', topK = {valid_req.topK}")
    
    # query too short
    try:
        RagRetrieveRequest(query="a")
        print("  ✗ FAIL: Should reject query < 2 chars")
    except ValidationError:
        print("  ✓ Correctly rejects query < 2 chars")
    
    # topK out of range
    try:
        RagRetrieveRequest(query="test query", topK=100)
        print("  ✗ FAIL: Should reject topK > 50")
    except ValidationError:
        print("  ✓ Correctly rejects topK > 50")


def test_contract_context_validation():
    """Test ContractContextRequest validation."""
    print("\nTesting ContractContextRequest...")
    
    # Valid request
    valid_req = ContractContextRequest(
        contractType="HOUSE_RENTAL",
        detectedClauseTypes=["PAYMENT", "DEPOSIT"],
    )
    print(f"  ✓ Valid request: contractType = {valid_req.contractType}")


if __name__ == "__main__":
    print("=" * 60)
    print("VALIDATION TESTS")
    print("=" * 60)
    
    test_classify_request_validation()
    test_analyze_request_validation()
    test_compare_request_validation()
    test_rag_retrieve_validation()
    test_contract_context_validation()
    
    print("\n" + "=" * 60)
    print("✓ ALL VALIDATION TESTS PASSED")
    print("=" * 60)
