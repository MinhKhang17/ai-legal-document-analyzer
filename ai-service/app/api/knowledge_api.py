from __future__ import annotations

import json
import logging
import os
import re
import threading
import time
from functools import lru_cache
from hashlib import md5
from typing import Any

from fastapi import APIRouter, File, Form, UploadFile
from pydantic import BaseModel, ConfigDict, Field

from app.core.config import settings
from app.models.knowledge_models import IngestionResult, RetrievedChunk
from app.services.gemini_client import GeminiClient
from app.services.knowledge_service import KnowledgeService, KnowledgeServiceV2
from app.services.llm_reranker import LlmReranker


class SearchChunksRequest(BaseModel):
    query: str = Field(..., description="Search query used to retrieve relevant chunks")
    top_k: int = Field(default=5, ge=1, le=20)


class AskRequest(BaseModel):
    query: str = Field(..., description="User question to search in imported legal knowledge")
    top_k: int = Field(default=5, ge=1, le=20)


QueryRequest = AskRequest


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


class AskResponse(BaseModel):
    answer: str
    llm_status: str
    llm_error: str | None = None
    chunks: list[QueryChunkResponse]


router = APIRouter(prefix="/api/knowledge", tags=["knowledge"])
logger = logging.getLogger(__name__)
_LLM_V2_CACHE: dict[str, str] = {}
RETRIEVAL_TOP_K = 12
RERANK_TOP_K = 5
FINAL_CONTEXT_CHUNKS = 3
MAX_CONTEXT_TOKENS = 2200
MAX_CHUNK_TOKENS = 500
WARN_CHUNK_TOKENS = 400
LLM_CONCURRENCY_LIMIT = max(2, min(4, int(os.getenv("LLM_V2_MAX_CONCURRENCY", "4"))))
_LLM_V2_SEMAPHORE = threading.BoundedSemaphore(LLM_CONCURRENCY_LIMIT)


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
    try:
        response = await get_service().ingest_upload(file=file, title=title)
        _log_response("ingest", response)
        return response
    except Exception as exc:
        logger.exception("Failed to ingest knowledge document")
        raise


@router.post("/ingest-v2")
async def ingest_document_v2(
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
) -> IngestionResult:
    try:
        response = await get_service_v2().ingest_upload(file=file, title=title, ingestion_version=2)
        _log_response("ingest_v2", response)
        return response
    except Exception:
        logger.exception("Failed to ingest knowledge document v2")
        raise


@router.post("/search")
def search_chunks(payload: SearchChunksRequest) -> list[RetrievedChunk]:
    response = get_service().search(payload.query, top_k=payload.top_k)
    _log_response("search", response)
    return response


def _build_query_response(
    *,
    query: str,
    top_k: int,
    chunks: list[RetrievedChunk],
    source: str,
    answer_preview: str | None = None,
) -> QueryResponse:
    answer_parts = [chunk.text.strip() for chunk in chunks[:3] if chunk.text.strip()]
    return QueryResponse(
        query=query,
        answer_preview=answer_preview if answer_preview is not None else ("\n\n".join(answer_parts) if answer_parts else "Không đủ dữ liệu."),
        source=source,
        top_k=top_k,
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


@router.post("/ask", response_model=QueryResponse)
def ask_legal_knowledge(payload: AskRequest) -> QueryResponse:
    chunks = get_service().search(payload.query, top_k=payload.top_k)
    response = _build_query_response(
        query=payload.query,
        top_k=payload.top_k,
        chunks=chunks,
        source="neo4j://legal_chunk_embedding_index",
    )
    _log_response("ask", response)
    return response


@router.post("/v2/query", response_model=AskResponse)
def ask_legal_knowledge_v2(payload: AskRequest) -> AskResponse:
    started_at = time.perf_counter()
    retrieved_chunks = get_service().search(payload.query, top_k=RETRIEVAL_TOP_K)
    reranked_chunks = _rerank_chunks(payload.query, retrieved_chunks, top_k=RERANK_TOP_K)

    # LLM Reranking (optional, enabled via LLM_RERANK_ENABLED=true)
    if settings.llm_rerank_enabled:
        try:
            llm_reranker = LlmReranker()
            reranked_chunks = llm_reranker.rerank(payload.query, reranked_chunks)
        except Exception as exc:
            logger.warning("LLM reranking failed, using heuristic order: %s", exc)

    llm_chunks = _select_llm_chunks(reranked_chunks, token_budget=MAX_CONTEXT_TOKENS, max_chunks=FINAL_CONTEXT_CHUNKS)
    llm_enabled = _is_llm_v2_enabled()
    if llm_enabled:
        answer, llm_error, model_name, output_tokens = _call_llm_v2(payload.query, llm_chunks)
        if not answer:
            answer = _build_retrieval_only_answer(llm_chunks or reranked_chunks or retrieved_chunks)
        llm_status = "ok" if not llm_error else "failed"
    else:
        answer = _build_retrieval_only_answer(llm_chunks or reranked_chunks or retrieved_chunks)
        llm_error = "LLM v2 is disabled by configuration"
        model_name = settings.gemini_model
        output_tokens = 0
        llm_status = "disabled"

    response_chunks = llm_chunks or reranked_chunks or retrieved_chunks
    response = AskResponse(
        answer=answer,
        llm_status=llm_status,
        llm_error=llm_error,
        chunks=[
            QueryChunkResponse(
                chunk_id=chunk.chunk_id,
                title=chunk.title,
                text=chunk.text,
                score=chunk.score,
                context=chunk.context,
            )
            for chunk in response_chunks
        ],
    )

    if llm_error and llm_enabled:
        logger.warning("ask_v2 llm_error=%s", llm_error)

    _log_ask_v2_observability(
        query=payload.query,
        retrieved_count=len(retrieved_chunks),
        reranked_count=len(reranked_chunks),
        final_context_chunks=len(llm_chunks),
        context_chunks=llm_chunks,
        model=model_name,
        latency_seconds=time.perf_counter() - started_at,
        llm_error=llm_error,
        output_tokens=output_tokens,
        llm_enabled=llm_enabled,
    )
    _log_response("ask_v2", response)
    return response


def _build_gemini_client(*, model: str | None = None) -> GeminiClient:
    api_key = os.getenv("GEMINI_API_KEY", settings.gemini_api_key).strip()
    resolved_model = (model or os.getenv("GEMINI_MODEL", settings.gemini_model)).strip()
    base_url = os.getenv("GEMINI_BASE_URL", settings.gemini_base_url).strip()
    timeout_seconds = float(os.getenv("GEMINI_TIMEOUT_SECONDS", str(settings.gemini_timeout_seconds)))
    max_output_tokens = int(os.getenv("GEMINI_MAX_OUTPUT_TOKENS", str(settings.gemini_max_output_tokens)))
    max_retries = int(os.getenv("GEMINI_MAX_RETRIES", str(settings.gemini_max_retries)))
    retry_backoff_seconds = float(os.getenv("GEMINI_RETRY_BACKOFF_SECONDS", str(settings.gemini_retry_backoff_seconds)))
    return GeminiClient(
        api_key=api_key,
        model=resolved_model,
        base_url=base_url,
        timeout_seconds=timeout_seconds,
        max_output_tokens=max_output_tokens,
        max_retries=max_retries,
        retry_backoff_seconds=retry_backoff_seconds,
    )


def _is_llm_v2_enabled() -> bool:
    env_value = os.getenv("LLM_V2_ENABLED")
    if env_value is not None:
        return env_value.strip().lower() in {"1", "true", "yes", "on"}
    return bool(settings.llm_v2_enabled and settings.llm_provider.strip().lower() == "gemini")


def _compact_whitespace(text: str) -> str:
    return " ".join(text.split()).strip()


def _compact_chunk_text(text: str, max_chars: int = 260) -> str:
    compact = _compact_whitespace(text)
    if len(compact) <= max_chars:
        return compact
    return compact[:max_chars].rsplit(" ", 1)[0].strip()


def _compact_chunk_tokens(text: str, max_tokens: int) -> str:
    compact = _compact_whitespace(text)
    tokens = compact.split()
    if len(tokens) <= max_tokens:
        return compact
    return " ".join(tokens[:max_tokens]).strip()


def _estimate_tokens(text: str) -> int:
    return len(_compact_whitespace(text).split())


def _extract_legal_path(chunk: RetrievedChunk) -> str:
    path_parts: list[str] = []
    for block in chunk.context:
        if block.get("type") != "ancestors":
            continue
        for node in block.get("nodes", []):
            title = _compact_whitespace(str(node.get("title") or ""))
            if title and title not in path_parts:
                path_parts.append(title)
    return " > ".join(path_parts[:4])


def _extract_legal_location(chunk: RetrievedChunk) -> tuple[str | None, str]:
    document_title: str | None = None
    location_parts: list[str] = []
    for block in chunk.context:
        if block.get("type") != "ancestors":
            continue
        for node in block.get("nodes", []):
            title = _compact_whitespace(str(node.get("title") or ""))
            label = _compact_whitespace(str(node.get("label") or ""))
            if not title:
                continue
            if label == "Document" and document_title is None:
                document_title = title
                continue
            if title not in location_parts:
                location_parts.append(title)
    if not location_parts:
        fallback_location = _compact_whitespace(chunk.title or "")
        if fallback_location:
            location_parts.append(fallback_location)
    return document_title, " > ".join(location_parts[:3])


def _normalize_path_key(path: str) -> str:
    return _compact_whitespace(path).lower()


def _chunk_group_key(chunk: RetrievedChunk) -> str:
    legal_path = _extract_legal_path(chunk)
    if legal_path:
        return _normalize_path_key(legal_path)
    return _normalize_path_key(chunk.title or chunk.chunk_id)


def _legal_score_boost(query: str, chunk: RetrievedChunk) -> float:
    query_tokens = set(_compact_whitespace(query).lower().split())
    text_tokens = set(_compact_whitespace(f"{chunk.title} {chunk.text} {_extract_legal_path(chunk)}").lower().split())
    if not query_tokens or not text_tokens:
        return 0.0
    overlap = len(query_tokens & text_tokens)
    return overlap / max(1, len(query_tokens))


def _rerank_chunks(query: str, chunks: list[RetrievedChunk], *, top_k: int) -> list[RetrievedChunk]:
    if not chunks or top_k <= 0:
        return []

    ranked: list[tuple[float, RetrievedChunk]] = []
    for chunk in chunks:
        token_count = _estimate_tokens(chunk.text)
        if token_count > MAX_CHUNK_TOKENS:
            logger.warning("Skipping oversized chunk during rerank chunk_id=%s tokens=%s", chunk.chunk_id, token_count)
            continue
        score = float(chunk.score) + _legal_score_boost(query, chunk)
        ranked.append((score, chunk))

    ranked.sort(key=lambda item: (item[0], item[1].score), reverse=True)
    deduped: list[RetrievedChunk] = []
    seen: set[str] = set()
    for _, chunk in ranked:
        dedupe_key = _normalize_path_key(f"{_chunk_group_key(chunk)}::{_compact_whitespace(chunk.text)[:240]}")
        if dedupe_key in seen:
            continue
        seen.add(dedupe_key)
        deduped.append(chunk)
        if len(deduped) >= top_k:
            break
    return deduped


def _select_llm_chunks(
    chunks: list[RetrievedChunk],
    *,
    token_budget: int,
    max_chunks: int,
) -> list[RetrievedChunk]:
    if not chunks or token_budget <= 0 or max_chunks <= 0:
        return []

    candidates = sorted(chunks, key=lambda chunk: chunk.score, reverse=True)
    selected: list[RetrievedChunk] = []
    seen: set[str] = set()
    used_tokens = 0

    for chunk in candidates:
        if chunk.score < 0.12:
            continue
        group_key = _chunk_group_key(chunk)
        dedupe_key = _normalize_path_key(f"{group_key}::{chunk.text}")
        if dedupe_key in seen:
            continue

        legal_path = _extract_legal_path(chunk)
        _, legal_location = _extract_legal_location(chunk)
        estimated_tokens = _estimate_tokens(chunk.text) + _estimate_tokens(legal_path) + _estimate_tokens(legal_location) + 16
        if estimated_tokens > MAX_CHUNK_TOKENS:
            logger.warning("Rejecting chunk above token limit chunk_id=%s tokens=%s", chunk.chunk_id, estimated_tokens)
            continue
        if selected and used_tokens + estimated_tokens > token_budget:
            continue
        if estimated_tokens > WARN_CHUNK_TOKENS:
            logger.warning("Chunk above warning threshold chunk_id=%s tokens=%s", chunk.chunk_id, estimated_tokens)

        selected.append(
            RetrievedChunk(
                chunk_id=chunk.chunk_id,
                text=_compact_chunk_tokens(chunk.text, MAX_CHUNK_TOKENS),
                score=chunk.score,
                title=chunk.title,
                context=chunk.context,
            )
        )
        seen.add(dedupe_key)
        used_tokens += estimated_tokens
        if len(selected) >= max_chunks:
            break

    return selected


def _build_llm_prompt(query: str, chunks: list[RetrievedChunk]) -> tuple[str, str]:
    selected = chunks[:FINAL_CONTEXT_CHUNKS]
    system_prompt = (
        "You are a legal RAG assistant.\n\n"
        "Rules:\n\n"
        "* Answer only using the provided context.\n"
        "* Do not invent information.\n"
        "* If the answer cannot be derived from the context, respond:\n"
        '  "Không đủ dữ liệu trong tài liệu được cung cấp."\n'
        "* Cite the document and legal location when possible."
    )
    if not selected:
        return system_prompt, f"Question:\n{_compact_whitespace(query)}\n\nContext:\n\nAnswer in Vietnamese."

    context_blocks: list[str] = []
    for index, chunk in enumerate(selected, start=1):
        document_title, legal_location = _extract_legal_location(chunk)
        legal_path = _extract_legal_path(chunk)
        compact_chunk = _compact_chunk_tokens(chunk.text, MAX_CHUNK_TOKENS)
        block_lines = [f"[Source {index}]"]
        block_lines.append(f"Document: {document_title or chunk.title or ''}")
        block_lines.append(f"Location: {legal_location or legal_path or chunk.title or ''}")
        block_lines.append("Content:")
        block_lines.append(compact_chunk)
        context_blocks.append("\n".join(block_lines).strip())

    user_prompt = (
        f"Question:\n{_compact_whitespace(query)}\n\n"
        f"Context:\n\n"
        + "\n\n".join(context_blocks)
        + "\n\nAnswer in Vietnamese."
    )
    return system_prompt, user_prompt


def _build_v2_cache_key(query: str, chunks: list[RetrievedChunk]) -> str:
    payload = "\n".join(
        [
            _compact_whitespace(query),
            *[
                "|".join(
                    [
                        _compact_whitespace(chunk.chunk_id),
                        _compact_whitespace(chunk.title),
                        _compact_chunk_text(chunk.text, max_chars=240),
                    ]
                )
                for chunk in chunks[:3]
            ],
        ]
    )
    return md5(payload.encode("utf-8"), usedforsecurity=False).hexdigest()


def _call_llm_v2(query: str, chunks: list[RetrievedChunk]) -> tuple[str | None, str | None, str, int]:
    if not chunks:
        return None, "No matching chunks found in Neo4j", settings.gemini_model, 0

    cache_key = _build_v2_cache_key(query, chunks)
    if cache_key in _LLM_V2_CACHE:
        cached_text = _LLM_V2_CACHE[cache_key]
        return cached_text, None, settings.gemini_model, _estimate_tokens(cached_text)

    system_prompt, user_prompt = _build_llm_prompt(query, chunks)
    _log_gemini_request(
        query=query,
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        chunk_count=len(chunks),
    )
    acquired = _LLM_V2_SEMAPHORE.acquire(timeout=10)
    if not acquired:
        return None, "LLM concurrency limit reached", settings.gemini_model, 0

    try:
        response = _build_gemini_client().generate_text(system_prompt=system_prompt, user_prompt=user_prompt)
        model_name = settings.gemini_model

        if response.text:
            _LLM_V2_CACHE[cache_key] = response.text
        return response.text, response.error, model_name, _estimate_tokens(response.text or "")
    finally:
        _LLM_V2_SEMAPHORE.release()


def _build_retrieval_only_answer(chunks: list[RetrievedChunk]) -> str:
    if not chunks:
        return "Không đủ dữ liệu trong tài liệu được cung cấp."
    return _compact_chunk_text(chunks[0].text, max_chars=900) or "Không đủ dữ liệu trong tài liệu được cung cấp."


def _error_code_from_message(error_message: str | None) -> str | None:
    if not error_message:
        return None
    match = re.search(r"\bHTTP\s+(\d{3})\b", error_message)
    if match:
        return match.group(1)
    return "timeout" if "timed out" in error_message.lower() else None


def _log_ask_v2_observability(
    *,
    query: str,
    retrieved_count: int,
    reranked_count: int,
    final_context_chunks: int,
    context_chunks: list[RetrievedChunk],
    model: str,
    latency_seconds: float,
    llm_error: str | None,
    output_tokens: int,
    llm_enabled: bool,
) -> None:
    context_tokens = sum(_estimate_tokens(chunk.text) + _estimate_tokens(_extract_legal_path(chunk)) for chunk in context_chunks)
    prompt_tokens = _estimate_tokens(query) + context_tokens + 120
    logger.info(
        "ask_v2_observability=%s",
        json.dumps(
            {
                "query": query,
                "retrieved_count": retrieved_count,
                "reranked_count": reranked_count,
                "final_context_chunks": final_context_chunks,
                "prompt_tokens": prompt_tokens,
                "context_tokens": context_tokens,
                "output_tokens": output_tokens,
                "model": model,
                "llm_enabled": llm_enabled,
                "latency": round(latency_seconds, 3),
                "error_code": _error_code_from_message(llm_error),
                "error_message": llm_error,
            },
            ensure_ascii=False,
            default=str,
        ),
    )


def _truncate_for_log(text: str, limit: int = 12000) -> str:
    compact = text if len(text) <= limit else text[:limit] + "\n...[TRUNCATED]..."
    return compact


def _log_gemini_request(*, query: str, system_prompt: str, user_prompt: str, chunk_count: int) -> None:
    logger.info(
        "ask_v2_gemini_request=%s",
        json.dumps(
            {
                "query": query,
                "chunk_count": chunk_count,
                "system_prompt": _truncate_for_log(system_prompt),
                "user_prompt": _truncate_for_log(user_prompt),
            },
            ensure_ascii=False,
            default=str,
        ),
    )


def _log_response(label: str, response: Any) -> None:
    if hasattr(response, "model_dump"):
        payload = response.model_dump()
    else:
        payload = response
    logger.info("%s response=%s", label, json.dumps(payload, ensure_ascii=False, default=str))
