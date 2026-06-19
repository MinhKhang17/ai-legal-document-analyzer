from __future__ import annotations

import json
import logging
import os
from hashlib import md5
from functools import lru_cache
from typing import Any

from fastapi import APIRouter, File, Form, UploadFile
from pydantic import BaseModel, ConfigDict, Field

from app.core.config import settings
from app.models.knowledge_models import IngestionResult, RetrievedChunk
from app.services.gemini_client import GeminiClient
from app.services.knowledge_service import KnowledgeService, KnowledgeServiceV2
from app.services.risk_knowledge_service import RiskKnowledgeService, RiskKnowledgeServiceV2


class SearchChunksRequest(BaseModel):
    query: str = Field(..., description="Search query used to retrieve relevant chunks")
    top_k: int = Field(default=5, ge=1, le=20)


class QueryRequest(BaseModel):
    query: str = Field(..., description="User question to search in imported legal knowledge")
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


class AskRequest(BaseModel):
    query: str = Field(..., description="User question to answer from imported legal knowledge")
    top_k: int = Field(default=5, ge=1, le=20)


class AskResponse(BaseModel):
    query: str
    answer: str
    source: str
    top_k: int
    chunks: list[QueryChunkResponse]
    model: str | None = None
    retrieval_count: int = 0
    llm_status: str = "not_called"
    llm_error: str | None = None


router = APIRouter(prefix="/knowledge", tags=["knowledge"])
_LLM_V2_CACHE: dict[str, str] = {}
logger = logging.getLogger(__name__)


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


@router.post("/ingest")
async def ingest_document(
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
) -> IngestionResult:
    response = await get_service().ingest_upload(file=file, title=title)
    _log_response("ingest", response)
    return response


@router.post("/ingest-v2")
async def ingest_document_v2(
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
) -> IngestionResult:
    response = await get_service_v2().ingest_upload(file=file, title=title, ingestion_version=2)
    _log_response("ingest_v2", response)
    return response


@router.post("/search")
def search_chunks(payload: SearchChunksRequest) -> list[RetrievedChunk]:
    response = get_service().search(payload.query, top_k=payload.top_k)
    _log_response("search", response)
    return response


@lru_cache(maxsize=1)
def get_legal_service() -> RiskKnowledgeService:
    return RiskKnowledgeService()


@lru_cache(maxsize=1)
def get_legal_service_v2() -> RiskKnowledgeServiceV2:
    return RiskKnowledgeServiceV2()


@router.post("/query", response_model=QueryResponse)
def query_legal_knowledge(payload: QueryRequest) -> QueryResponse:
    chunks = get_legal_service().search(payload.query, top_k=payload.top_k)
    answer_parts = [chunk.text.strip() for chunk in chunks[:3] if chunk.text.strip()]
    response = QueryResponse(
        query=payload.query,
        answer_preview="\n\n".join(answer_parts),
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
def query_legal_knowledge_v2(payload: QueryRequest) -> QueryResponse:
    chunks = get_legal_service_v2().search(payload.query, top_k=payload.top_k)
    answer_parts = [chunk.text.strip() for chunk in chunks[:3] if chunk.text.strip()]
    response = QueryResponse(
        query=payload.query,
        answer_preview="\n\n".join(answer_parts),
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


def _clean_chunk_text(text: str, max_chars: int = 220) -> str:
    compact = " ".join(text.split())
    if len(compact) <= max_chars:
        return compact
    return compact[:max_chars].rsplit(" ", 1)[0].strip() + "..."


def _compact_chunk_text(text: str, max_chars: int = 260) -> str:
    compact = " ".join(text.split())
    if len(compact) <= max_chars:
        return compact
    return compact[:max_chars].rsplit(" ", 1)[0].strip()


def _compact_whitespace(text: str) -> str:
    return " ".join(text.split()).strip()


def _build_gemini_client() -> GeminiClient:
    api_key = os.getenv("GEMINI_API_KEY", settings.gemini_api_key).strip()
    model = os.getenv("GEMINI_MODEL", settings.gemini_model).strip()
    base_url = os.getenv("GEMINI_BASE_URL", settings.gemini_base_url).strip()
    timeout_seconds = float(os.getenv("GEMINI_TIMEOUT_SECONDS", str(settings.gemini_timeout_seconds)))
    max_output_tokens = int(os.getenv("GEMINI_MAX_OUTPUT_TOKENS", str(settings.gemini_max_output_tokens)))
    return GeminiClient(
        api_key=api_key,
        model=model,
        base_url=base_url,
        timeout_seconds=timeout_seconds,
        max_output_tokens=max_output_tokens,
    )


def _should_use_llm_v2() -> bool:
    raw_flag = os.getenv("LLM_V2_ENABLED")
    if raw_flag is not None and raw_flag.strip():
        return raw_flag.lower().strip() in {"1", "true", "yes", "on"}

    provider = os.getenv("LLM_PROVIDER", settings.llm_provider).lower().strip()
    api_key = os.getenv("GEMINI_API_KEY", settings.gemini_api_key).strip()
    model = os.getenv("GEMINI_MODEL", settings.gemini_model).strip()
    return provider == "gemini" and bool(api_key) and bool(model)


def _build_v2_cache_key(query: str, chunks: list[RetrievedChunk]) -> str:
    payload = "\n".join(
        [
            _compact_whitespace(query),
            *[
                "|".join(
                    [
                        _compact_whitespace(chunk.chunk_id),
                        _compact_whitespace(chunk.title),
                        _compact_whitespace(chunk.text),
                    ]
                )
                for chunk in chunks[:1]
            ],
        ]
    )
    return md5(payload.encode("utf-8"), usedforsecurity=False).hexdigest()


def _log_response(label: str, response: Any) -> None:
    if hasattr(response, "model_dump"):
        payload = response.model_dump()
    else:
        payload = response
    logger.info("%s response=%s", label, json.dumps(payload, ensure_ascii=False, default=str))


def _extract_lineage_text(chunk: RetrievedChunk) -> str:
    lines: list[str] = []
    for block in chunk.context:
        if block.get("type") != "lineage":
            continue
        for node in block.get("nodes", []):
            title = _compact_whitespace(str(node.get("title") or ""))
            text = _compact_whitespace(str(node.get("text") or ""))
            if title and text and title != text:
                lines.append(f"{title}: {text}")
            elif title:
                lines.append(title)
            elif text:
                lines.append(text)
    return "\n".join(line for line in lines if line)


def _should_expand_parent_context(chunk: RetrievedChunk) -> bool:
    content_tokens = len(_compact_whitespace(chunk.text).split())
    if content_tokens < 80:
        return True
    if chunk.score < 0.82:
        return True
    return False


def _build_llm_context(chunks: list[RetrievedChunk]) -> str:
    selected = chunks[:1]
    if not selected:
        return ""
    chunk = selected[0]
    title = _compact_whitespace(chunk.title)
    text = _clean_chunk_text(chunk.text)
    if title:
        return f"{title}: {text}"
    return text


def _build_llm_messages(query: str, chunks: list[RetrievedChunk]) -> tuple[str, str]:
    system_prompt = "Tráº£ lá»i ngáº¯n gá»n, chá»‰ dá»±a trÃªn context."
    context = _build_llm_context(chunks)
    user_prompt = f"Q:{_compact_whitespace(query)}\nC:{context}"
    return system_prompt, user_prompt


def _build_llm_messages_v2(query: str, chunks: list[RetrievedChunk]) -> tuple[str, str]:
    system_prompt = "Tráº£ lá»i ngáº¯n gá»n, chá»‰ dá»±a trÃªn context."
    context = _build_llm_context(chunks)
    user_prompt = f"Q:{_compact_whitespace(query)}\nC:{context}"
    return system_prompt, user_prompt


def _call_llm(query: str, chunks: list[RetrievedChunk]) -> tuple[str | None, str | None]:
    system_prompt, user_prompt = _build_llm_messages(query, chunks)
    response = _build_gemini_client().generate_text(system_prompt=system_prompt, user_prompt=user_prompt)
    return response.text, response.error


def _call_llm_v2(query: str, chunks: list[RetrievedChunk]) -> tuple[str | None, str | None]:
    cache_key = _build_v2_cache_key(query, chunks)
    if cache_key in _LLM_V2_CACHE:
        return _LLM_V2_CACHE[cache_key], None

    system_prompt, user_prompt = _build_llm_messages_v2(query, chunks)
    response = _build_gemini_client().generate_text(system_prompt=system_prompt, user_prompt=user_prompt)
    if response.text:
        _LLM_V2_CACHE[cache_key] = response.text
    return response.text, response.error


def _build_retrieval_only_answer(chunks: list[RetrievedChunk]) -> str:
    if not chunks:
        return "KhÃ´ng Ä‘á»§ dá»¯ liá»‡u."
    return _compact_chunk_text(chunks[0].text, max_chars=900) or "KhÃ´ng Ä‘á»§ dá»¯ liá»‡u."


@router.post("/ask", response_model=AskResponse)
def ask_legal_knowledge(payload: AskRequest) -> AskResponse:
    chunks = get_legal_service().search(payload.query, top_k=payload.top_k)
    if not chunks:
        response = AskResponse(
            query=payload.query,
            answer="KhÃ´ng Ä‘á»§ dá»¯ liá»‡u.",
            source="neo4j://legal_chunk_embedding_index",
            top_k=payload.top_k,
            chunks=[],
            model=os.getenv("GEMINI_MODEL", settings.gemini_model).strip() or None,
            retrieval_count=0,
            llm_status="no_retrieval",
            llm_error="No matching chunks found in Neo4j",
        )
        _log_response("ask", response)
        return response
    answer, llm_error = _call_llm(payload.query, chunks[:1])
    if not answer:
        answer = "KhÃ´ng Ä‘á»§ dá»¯ liá»‡u."
    response = AskResponse(
        query=payload.query,
        answer=answer,
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
        model=os.getenv("GEMINI_MODEL", settings.gemini_model).strip() or None,
        retrieval_count=len(chunks),
        llm_status="ok" if llm_error is None else "failed",
        llm_error=llm_error,
    )
    _log_response("ask", response)
    return response


@router.post("/ask-v2", response_model=AskResponse)
def ask_legal_knowledge_v2(payload: AskRequest) -> AskResponse:
    chunks = get_legal_service_v2().search(payload.query, top_k=payload.top_k)
    if not chunks:
        response = AskResponse(
            query=payload.query,
            answer="KhÃ´ng Ä‘á»§ dá»¯ liá»‡u.",
            source="neo4j://legal_chunk_embedding_index?v=2",
            top_k=payload.top_k,
            chunks=[],
            model=os.getenv("GEMINI_MODEL", settings.gemini_model).strip() or None,
            retrieval_count=0,
            llm_status="no_retrieval",
            llm_error="No matching chunks found in Neo4j",
        )
        _log_response("ask_v2", response)
        return response
    top_chunks = chunks[:1]
    if not _should_use_llm_v2():
        response = AskResponse(
            query=payload.query,
            answer=_build_retrieval_only_answer(top_chunks),
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
            model=None,
            retrieval_count=len(chunks),
            llm_status="disabled",
            llm_error=None,
        )
        _log_response("ask_v2", response)
        return response
    answer, llm_error = _call_llm_v2(payload.query, top_chunks)
    if not answer:
        answer = _build_retrieval_only_answer_v2(top_chunks)
    response = AskResponse(
        query=payload.query,
        answer=answer,
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
        model=os.getenv("GEMINI_MODEL", settings.gemini_model).strip() or None,
        retrieval_count=len(chunks),
        llm_status="ok" if llm_error is None else "failed",
        llm_error=llm_error,
    )
    _log_response("ask_v2", response)
    return response


def _build_retrieval_only_answer_v2(chunks: list[RetrievedChunk]) -> str:
    if not chunks:
        return "KhÃ´ng Ä‘á»§ dá»¯ liá»‡u."
    return _compact_chunk_text(chunks[0].text, max_chars=900) or "KhÃ´ng Ä‘á»§ dá»¯ liá»‡u."
