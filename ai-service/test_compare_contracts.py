"""Smoke test: compare-contracts returns clauseComparisons.

Uses the service layer directly. Verifies clean fallback when LLM not set.

Run: python test_compare_contracts.py
"""
from app.models.legal_models import (
    AnalysisOptions,
    CompareContractsRequest,
    ContractType,
)
from app.services.legal_service import LegalService

DOC_A = (
    "HỢP ĐỒNG THUÊ NHÀ. Bên cho thuê và bên thuê. Đối tượng căn hộ. "
    "Tiền thuê thanh toán hàng tháng. Đặt cọc 2 tháng. Điều khoản chấm dứt. "
    "Phạt vi phạm. Giải quyết tranh chấp tại tòa án."
)
DOC_B = (
    "HỢP ĐỒNG THUÊ NHÀ. Bên cho thuê và bên thuê. Đối tượng căn hộ. "
    "Tiền thuê thanh toán hàng tháng. Bàn giao nhà."
)


def main() -> None:
    svc = LegalService()
    req = CompareContractsRequest(
        documentAText=DOC_A,
        documentBText=DOC_B,
        contractType=ContractType.HOUSE_RENTAL,
        protectedParty="bên thuê",
        options=AnalysisOptions(outputMode="JSON", includeGraphContext=True),
    )
    result = svc.compare(req)

    print(f"[INFO] favorable={result['moreFavorableVersion']} "
          f"overall={result['overallRiskLevel']} "
          f"llmUsed={result['llmUsed']} fallback={result['fallback']}")
    print(f"[INFO] clauseComparisons={len(result['clauseComparisons'])}")
    for c in result["clauseComparisons"]:
        print(f"   - {c['topic']}: {c['differenceType']} ({c['riskLevel']})")

    assert isinstance(result["clauseComparisons"], list)
    assert len(result["clauseComparisons"]) > 0
    assert result["moreFavorableVersion"] in {"A", "B", "EQUAL", "INSUFFICIENT_DATA"}
    # Consistent regardless of whether an LLM is configured.
    assert result["llmUsed"] != result["fallback"]
    mode = "LLM" if result["llmUsed"] else "fallback (no LLM)"
    print(f"[OK] compare-contracts returned clauseComparisons ({mode})")


if __name__ == "__main__":
    main()
