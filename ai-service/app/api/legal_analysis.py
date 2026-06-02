"""Legal Analysis API: classify, analyze and compare contracts."""
from fastapi import APIRouter, Depends

from app.api.deps import get_trace_id
from app.models.common import success_payload
from app.models.legal_models import (
    AnalyzeContractRequest,
    ClassifyContractRequest,
    CompareContractsRequest,
)
from app.services.legal_service import LegalService

router = APIRouter(prefix="/legal", tags=["legal"])


def get_legal_service() -> LegalService:
    return LegalService()


@router.post("/classify-contract")
def classify_contract(
    req: ClassifyContractRequest,
    trace_id: str = Depends(get_trace_id),
    svc: LegalService = Depends(get_legal_service),
):
    data = svc.classify(req.text)
    return success_payload(data, "Contract classified", trace_id)


@router.post("/analyze-contract")
def analyze_contract(
    req: AnalyzeContractRequest,
    trace_id: str = Depends(get_trace_id),
    svc: LegalService = Depends(get_legal_service),
):
    data = svc.analyze(req)
    message = "Contract analyzed" if not data.get("fallback") else "Contract analyzed (fallback, no LLM)"
    return success_payload(data, message, trace_id)


@router.post("/compare-contracts")
def compare_contracts(
    req: CompareContractsRequest,
    trace_id: str = Depends(get_trace_id),
    svc: LegalService = Depends(get_legal_service),
):
    data = svc.compare(req)
    message = "Contracts compared" if not data.get("fallback") else "Contracts compared (fallback, no LLM)"
    return success_payload(data, message, trace_id)
