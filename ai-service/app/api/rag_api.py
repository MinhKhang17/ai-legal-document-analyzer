from __future__ import annotations

from functools import lru_cache

from fastapi import APIRouter, HTTPException

from app.schemas import RagPreviewResponse, RagQueryRequest, RagQueryResponse
from app.services.rag_query_service import LlmGenerationError, RagQueryService


router = APIRouter(prefix="/internal/rag", tags=["internal-rag"])


@lru_cache(maxsize=1)
def get_rag_query_service() -> RagQueryService:
    return RagQueryService()


@router.post("/query", response_model=RagQueryResponse)
def query_rag(payload: RagQueryRequest) -> RagQueryResponse:
    try:
        return get_rag_query_service().query(payload)
    except LlmGenerationError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@router.post("/preview", response_model=RagPreviewResponse)
def preview_rag(payload: RagQueryRequest) -> RagPreviewResponse:
    return get_rag_query_service().preview(payload)
