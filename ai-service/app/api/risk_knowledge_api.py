from __future__ import annotations

import json
import logging
from functools import lru_cache

from fastapi import APIRouter, File, Form, UploadFile
from pydantic import BaseModel, ConfigDict, Field

from app.models.knowledge_models import IngestionResult
from app.services.risk_knowledge_service import RiskKnowledgeService, RiskKnowledgeServiceV2


class QueryRequest(BaseModel):
    query: str = Field(..., description="User question to search in imported risk data")
    top_k: int = Field(default=5, ge=1, le=20)


class QueryChunkResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    chunk_id: str
    title: str
    text: str
    score: float
    context: list[dict]


class QueryResponse(BaseModel):
    query: str
    answer_preview: str
    source: str
    top_k: int
    chunks: list[QueryChunkResponse]


router = APIRouter(prefix="/admin/risk-knowledge", tags=["risk-knowledge"])
logger = logging.getLogger(__name__)


def _log_response(label: str, response) -> None:
    if hasattr(response, "model_dump"):
        payload = response.model_dump()
    else:
        payload = response
    logger.info("%s response=%s", label, json.dumps(payload, ensure_ascii=False, default=str))


@lru_cache(maxsize=1)
def get_service() -> RiskKnowledgeService:
    return RiskKnowledgeService()


@lru_cache(maxsize=1)
def get_service_v2() -> RiskKnowledgeServiceV2:
    return RiskKnowledgeServiceV2()


@router.get("/supported-formats")
def supported_formats() -> list[str]:
    response = get_service().supported_formats()
    _log_response("supported_formats", response)
    return response


@router.post("/import")
async def import_risk_document(
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
) -> IngestionResult:
    response = await get_service().ingest_upload(file=file, title=title)
    _log_response("import", response)
    return response


@router.post("/import-v2")
async def import_risk_document_v2(
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
) -> IngestionResult:
    response = await get_service_v2().ingest_upload(file=file, title=title, ingestion_version=2)
    _log_response("import_v2", response)
    return response


@router.post("/query", response_model=QueryResponse)
def query_risk_knowledge(payload: QueryRequest) -> QueryResponse:
    chunks = get_service().search(payload.query, top_k=payload.top_k)
    answer_parts = [chunk.text.strip() for chunk in chunks[:3] if chunk.text.strip()]
    answer_preview = "\n\n".join(answer_parts)
    response = QueryResponse(
        query=payload.query,
        answer_preview=answer_preview,
        source="neo4j://legal_chunk_embedding_index",
        top_k=payload.top_k,
        chunks=[
            QueryChunkResponse(
                chunk_id=chunk.chunk_id,
                title=chunk.title,
                text=chunk.text,
                score=chunk.score,
                context=chunk.context,
            )
            for chunk in chunks
        ],
    )
    _log_response("query", response)
    return response


@router.post("/query-v2", response_model=QueryResponse)
def query_risk_knowledge_v2(payload: QueryRequest) -> QueryResponse:
    chunks = get_service_v2().search(payload.query, top_k=payload.top_k)
    answer_parts = [chunk.text.strip() for chunk in chunks[:3] if chunk.text.strip()]
    answer_preview = "\n\n".join(answer_parts)
    response = QueryResponse(
        query=payload.query,
        answer_preview=answer_preview,
        source="neo4j://legal_chunk_embedding_index?v=2",
        top_k=payload.top_k,
        chunks=[
            QueryChunkResponse(
                chunk_id=chunk.chunk_id,
                title=chunk.title,
                text=chunk.text,
                score=chunk.score,
                context=chunk.context,
            )
            for chunk in chunks
        ],
    )
    _log_response("query_v2", response)
    return response
