from __future__ import annotations

import logging

from app.schemas import RagCitation, RagPreviewChunk, RagPreviewResponse, RagQueryRequest, RagQueryResponse
from app.services.llm_client import RagLlmClient, build_default_llm_client
from app.services.prompt_builder import build_system_prompt, build_user_prompt
from app.services.query_builder import build_legal_search_query
from app.services.retrieval_service import RagChunkHit, RetrievalService


logger = logging.getLogger(__name__)


class RagQueryService:
    def __init__(
        self,
        *,
        retrieval_service: RetrievalService | None = None,
        llm_client: RagLlmClient | None = None,
    ) -> None:
        self.retrieval_service = retrieval_service or RetrievalService()
        self.llm_client = llm_client or build_default_llm_client()

    def query(self, request: RagQueryRequest) -> RagQueryResponse:
        user_hits, legal_search_query, knowledge_hits = self._retrieve(request)

        system_prompt = build_system_prompt()
        user_prompt = build_user_prompt(request.question, user_hits, knowledge_hits)
        llm_result = self.llm_client.generate(system_prompt=system_prompt, user_prompt=user_prompt)

        answer = llm_result.answer or self._build_fallback_answer(user_hits, knowledge_hits)
        risk_level = llm_result.risk_level if llm_result.risk_level else "UNKNOWN"
        if risk_level not in {"LOW", "MEDIUM", "HIGH", "NEED_EXPERT", "UNKNOWN"}:
            risk_level = "UNKNOWN"

        citations = [self._to_citation(hit) for hit in [*user_hits, *knowledge_hits]]
        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=answer,
            riskLevel=risk_level,
            citations=citations,
            retrievedUserChunks=len(user_hits),
            retrievedKnowledgeChunks=len(knowledge_hits),
        )

    def preview(self, request: RagQueryRequest) -> RagPreviewResponse:
        user_hits, legal_search_query, knowledge_hits = self._retrieve(request)
        return RagPreviewResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            question=request.question,
            legalSearchQuery=legal_search_query,
            userChunks=[self._to_preview_chunk(hit) for hit in user_hits],
            knowledgeChunks=[self._to_preview_chunk(hit) for hit in knowledge_hits],
            retrievedUserChunks=len(user_hits),
            retrievedKnowledgeChunks=len(knowledge_hits),
        )

    def _retrieve(self, request: RagQueryRequest) -> tuple[list[RagChunkHit], str, list[RagChunkHit]]:
        user_hits = self.retrieval_service.search_user_chunks(
            request.question,
            user_id=request.userId,
            workspace_id=request.workspaceId,
            top_k=request.topKUserChunks,
        )
        legal_search_query = build_legal_search_query(request.question, user_hits)
        knowledge_hits = self.retrieval_service.search_knowledge_chunks(
            legal_search_query,
            top_k=request.topKKnowledgeChunks,
            query_text=request.question,
        )
        return user_hits, legal_search_query, knowledge_hits

    def _build_fallback_answer(self, user_hits: list[RagChunkHit], knowledge_hits: list[RagChunkHit]) -> str:
        if not user_hits and not knowledge_hits:
            return "Không đủ dữ liệu trong tài liệu và Knowledge Base được cung cấp."

        parts: list[str] = []
        if user_hits:
            parts.append(f"Dựa trên tài liệu người dùng, có {len(user_hits)} đoạn liên quan.")
        if knowledge_hits:
            parts.append(f"Knowledge Base cung cấp {len(knowledge_hits)} đoạn pháp lý liên quan.")
        parts.append("Cần đối chiếu thêm để xác định mức độ rủi ro chính xác.")
        return " ".join(parts)

    def _to_citation(self, hit: RagChunkHit) -> RagCitation:
        return RagCitation(
            citationId=hit.citationId,
            sourceType=hit.sourceType,
            score=hit.score,
            documentId=hit.documentId,
            workspaceId=hit.workspaceId,
            userId=hit.userId,
            fileName=hit.fileName,
            knowledgeDocumentId=hit.knowledgeDocumentId,
            lawName=hit.lawName,
            lawCode=hit.lawCode,
            legalDomain=hit.legalDomain,
            pageNumber=hit.pageNumber,
            articleNumber=hit.articleNumber,
            clauseNumber=hit.clauseNumber,
            sectionTitle=hit.sectionTitle,
        )

    def _to_preview_chunk(self, hit: RagChunkHit) -> RagPreviewChunk:
        return RagPreviewChunk(
            citationId=hit.citationId,
            sourceType=hit.sourceType,
            score=hit.score,
            chunkText=hit.chunkText,
            documentId=hit.documentId,
            workspaceId=hit.workspaceId,
            userId=hit.userId,
            fileName=hit.fileName,
            knowledgeDocumentId=hit.knowledgeDocumentId,
            lawName=hit.lawName,
            lawCode=hit.lawCode,
            legalDomain=hit.legalDomain,
            pageNumber=hit.pageNumber,
            articleNumber=hit.articleNumber,
            clauseNumber=hit.clauseNumber,
            sectionTitle=hit.sectionTitle,
        )
