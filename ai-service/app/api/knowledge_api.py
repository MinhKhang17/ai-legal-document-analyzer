from __future__ import annotations

import json
import logging
from functools import lru_cache

from fastapi import APIRouter, File, Form, UploadFile
from pydantic import BaseModel, ConfigDict, Field

from app.models.knowledge_models import IngestionResult
from app.services.knowledge_service import KnowledgeService, KnowledgeServiceV2


class QueryRequest(BaseModel):
    query: str = Field(..., description="User question to search in knowledge base")
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


router = APIRouter(prefix="/api/knowledge", tags=["knowledge"])
logger = logging.getLogger(__name__)


def _log_response(label: str, response) -> None:
    if hasattr(response, "model_dump"):
        payload = response.model_dump()
    else:
        payload = response
    logger.info("%s response=%s", label, json.dumps(payload, ensure_ascii=False, default=str))


@lru_cache(maxsize=1)
def get_service() -> KnowledgeService:
    return KnowledgeService()


@lru_cache(maxsize=1)
def get_service_v2() -> KnowledgeServiceV2:
    return KnowledgeServiceV2()


@router.get("/supported-formats")
def supported_formats() -> list[str]:
    response = get_service().supported_formats()
    _log_response("supported_formats", response)
    return response


@router.post("/ingest", response_model=IngestionResult)
async def ingest_document(
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
) -> IngestionResult:
    response = await get_service().ingest_upload(file=file, title=title)
    _log_response("ingest", response)
    return response


@router.post("/ingest-v2", response_model=IngestionResult)
async def ingest_document_v2(
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
) -> IngestionResult:
    response = await get_service_v2().ingest_upload(file=file, title=title, ingestion_version=2)
    _log_response("ingest_v2", response)
    return response


@router.post("/search", response_model=QueryResponse)
def search_knowledge(payload: QueryRequest) -> QueryResponse:
    chunks = get_service().search(payload.query, top_k=payload.top_k)
    answer_parts = [chunk.text.strip() for chunk in chunks[:3] if chunk.text.strip()]
    response = QueryResponse(
        query=payload.query,
        answer_preview="\n\n".join(answer_parts),
        source="neo4j://knowledge_embedding_index",
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
    _log_response("search", response)
    return response


@router.post("/ask", response_model=QueryResponse)
def ask_knowledge(payload: QueryRequest) -> QueryResponse:
    return search_knowledge(payload)


@router.post("/query-v2", response_model=QueryResponse)
def search_knowledge_v2(payload: QueryRequest) -> QueryResponse:
    chunks = get_service_v2().search(payload.query, top_k=payload.top_k)
    answer_parts = [chunk.text.strip() for chunk in chunks[:3] if chunk.text.strip()]
    response = QueryResponse(
        query=payload.query,
        answer_preview="\n\n".join(answer_parts),
        source="neo4j://knowledge_embedding_index?v=2",
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
    _log_response("search_v2", response)
    return response
