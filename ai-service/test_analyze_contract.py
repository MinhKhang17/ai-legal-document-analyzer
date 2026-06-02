"""Smoke test: analyze-contract returns structured JSON.

Uses the service layer directly (no HTTP server required). Verifies that with
LLM not configured the service returns a clean fallback instead of crashing.

Run: python test_analyze_contract.py
"""
from app.models.legal_models import AnalysisOptions, AnalyzeContractRequest, ContractType
from app.services.legal_service import LegalService

CONTRACT = (
    "HỢP ĐỒNG THUÊ NHÀ. Bên cho thuê: Nguyễn Văn A. Bên thuê: Trần Thị B. "
    "Đối tượng: căn hộ tại Hà Nội. Tiền thuê 10 triệu/tháng. Đặt cọc 2 tháng. "
    "Bàn giao ngày 01/01."
)


def main() -> None:
    svc = LegalService()
    req = AnalyzeContractRequest(
        contractText=CONTRACT,
        contractType=ContractType.HOUSE_RENTAL,
        protectedParty="bên thuê",
        question="Hợp đồng này có rủi ro gì?",
        options=AnalysisOptions(outputMode="JSON", includeGraphContext=True),
    )
    result = svc.analyze(req)

    print(f"[INFO] contractType={result['contractType']} "
          f"overall={result['overallRiskLevel']} "
          f"llmUsed={result['llmUsed']} fallback={result['fallback']}")
    print(f"[INFO] riskItems={len(result['riskItems'])} "
          f"missingClauses={result['missingClauses']}")

    assert result["contractType"] == "HOUSE_RENTAL"
    assert result["overallRiskLevel"] in {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
    assert isinstance(result["riskItems"], list)
    # Whether or not an LLM is configured, the result must be structured JSON.
    # llmUsed and fallback are mutually exclusive and consistent.
    assert result["llmUsed"] != result["fallback"]
    mode = "LLM" if result["llmUsed"] else "fallback (no LLM)"
    print(f"[OK] analyze-contract returned structured JSON ({mode})")


if __name__ == "__main__":
    main()
