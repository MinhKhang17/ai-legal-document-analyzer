"""RAG API: context retrieval and contract-context endpoints."""
from fastapi import APIRouter, Depends

from app.api.deps import get_trace_id
from app.models.common import success_payload
from app.models.rag_models import ContractContextRequest, RagRetrieveRequest
from app.services.rag_service import RagService

router = APIRouter(prefix="/rag", tags=["rag"])


def get_rag_service() -> RagService:
    return RagService()


@router.post("/retrieve")
def retrieve(
    req: RagRetrieveRequest,
    trace_id: str = Depends(get_trace_id),
    svc: RagService = Depends(get_rag_service),
):
    data = svc.retrieve(
        query=req.query,
        contract_type=req.contractType,
        top_k=req.topK,
        risk_filters=req.filters.riskTypes,
        clause_filters=req.filters.clauseTypes,
    )
    return success_payload(data, "Context retrieved", trace_id)


@router.post("/contract-context")
def contract_context(
    req: ContractContextRequest,
    trace_id: str = Depends(get_trace_id),
    svc: RagService = Depends(get_rag_service),
):
    data = svc.contract_context(
        contract_type=req.contractType,
        question=req.question,
        detected_clause_types=req.detectedClauseTypes,
    )
    return success_payload(data, "Contract context built", trace_id)
