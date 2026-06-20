from __future__ import annotations

from functools import lru_cache

from fastapi import APIRouter

from app.schemas import RagPreviewResponse, RagQueryRequest, RagQueryResponse
from app.services.rag_query_service import RagQueryService


router = APIRouter(prefix="/internal/rag", tags=["internal-rag"])


@lru_cache(maxsize=1)
def get_rag_query_service() -> RagQueryService:
    return RagQueryService()


@router.post("/query", response_model=RagQueryResponse)
def query_rag(payload: RagQueryRequest) -> RagQueryResponse:
    return get_rag_query_service().query(payload)


@router.post("/preview", response_model=RagPreviewResponse)
def preview_rag(payload: RagQueryRequest) -> RagPreviewResponse:
    return get_rag_query_service().preview(payload)
