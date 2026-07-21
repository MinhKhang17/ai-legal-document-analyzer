from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Literal

from app.core.knowledge_access import is_published_system_kb
from app.graph.repository import GraphRepository
from app.models.knowledge_models import RetrievedChunk
from app.services.embedding_service import EmbeddingService


@dataclass(frozen=True)
class RagChunkHit:
    citationId: str
    sourceType: Literal["USER_DOCUMENT", "SYSTEM_KB"]
    score: float
    chunkText: str
    documentId: str | None = None
    workspaceId: str | None = None
    userId: str | None = None
    fileName: str | None = None
    knowledgeDocumentId: str | None = None
    lawName: str | None = None
    lawCode: str | None = None
    legalDomain: str | None = None
    pageNumber: int | None = None
    articleNumber: str | None = None
    clauseNumber: str | None = None
    sectionTitle: str | None = None
    rawChunkId: str = ""
    title: str = ""
    metadata: dict[str, Any] | None = None
    sourceChunk: RetrievedChunk | None = None


class RetrievalService:
    def __init__(
        self,
        *,
        repository: GraphRepository | None = None,
        embedding_service: EmbeddingService | None = None,
    ) -> None:
        self.repository = repository or GraphRepository()
        self.embedding_service = embedding_service or EmbeddingService()

    def embed_question(self, text: str) -> list[float]:
        return self.embedding_service.embed_text(text)

    def search_user_chunks(
        self,
        question: str,
        user_id: str,
        workspace_id: str,
        top_k: int,
        document_id: str | None = None,
        document_ids: list[str] | None = None,
    ) -> list[RagChunkHit]:
        embedding = self.embed_question(question)
        chunks = self.repository.search_user_chunks(
            embedding,
            user_id=user_id,
            workspace_id=workspace_id,
            top_k=top_k,
            query_text=question,
            document_id=document_id,
            document_ids=document_ids,
        )
        return [
            self._to_hit(
                chunk,
                citation_id=f"USER-{index}",
                source_type="USER_DOCUMENT",
            )
            for index, chunk in enumerate(chunks, start=1)
        ]

    def build_legal_search_query(
        self,
        question: str,
        user_hits: list[RagChunkHit],
        *,
        chat_history: str | None = None,
    ) -> str:
        from app.services.query_builder import build_legal_search_query

        return build_legal_search_query(question, user_hits, chat_history=chat_history)

    def search_knowledge_chunks(
        self,
        legal_search_query: str,
        top_k: int,
        *,
        query_text: str | None = None,
    ) -> list[RagChunkHit]:
        embedding = self.embed_question(legal_search_query)
        chunks = self.repository.search_knowledge_chunks(
            embedding,
            top_k=top_k,
            query_text=query_text or legal_search_query,
        )
        chunks = [chunk for chunk in chunks if self._is_authoritative_system_kb_chunk(chunk)]
        return [
            self._to_hit(
                chunk,
                citation_id=f"KB-{index}",
                source_type="SYSTEM_KB",
            )
            for index, chunk in enumerate(chunks, start=1)
        ]

    @staticmethod
    def _is_authoritative_system_kb_chunk(chunk: RetrievedChunk) -> bool:
        metadata = dict(chunk.metadata or {})
        return is_published_system_kb(
            metadata,
            source_type=chunk.source_type,
        )

    def _to_hit(self, chunk: RetrievedChunk, *, citation_id: str, source_type: Literal["USER_DOCUMENT", "SYSTEM_KB"]) -> RagChunkHit:
        metadata = dict(chunk.metadata or {})
        document_id = str(metadata.get("document_id") or metadata.get("documentId") or "").strip() or None
        workspace_id = str(metadata.get("workspace_id") or metadata.get("workspaceId") or "").strip() or None
        user_id = str(metadata.get("user_id") or metadata.get("userId") or "").strip() or None
        file_name = str(
            metadata.get("file_name")
            or metadata.get("fileName")
            or metadata.get("source_file")
            or metadata.get("doc_title")
            or metadata.get("law_name")
            or chunk.title
            or ""
        ).strip() or None
        knowledge_document_id = None
        if source_type == "SYSTEM_KB":
            knowledge_document_id = str(
                metadata.get("knowledge_document_id")
                or metadata.get("knowledgeDocumentId")
                or metadata.get("document_id")
                or ""
            ).strip() or None
        law_name = str(metadata.get("law_name") or metadata.get("lawName") or "").strip() or None
        law_code = str(metadata.get("law_code") or metadata.get("lawCode") or "").strip() or None
        legal_domain = str(metadata.get("legal_domain") or metadata.get("legalDomain") or "").strip() or None
        page_number = metadata.get("page_number") or metadata.get("pageNumber")
        article_number = str(metadata.get("article_number") or metadata.get("articleNumber") or "").strip() or None
        clause_number = str(metadata.get("clause_number") or metadata.get("clauseNumber") or "").strip() or None
        section_title = str(metadata.get("section_title") or metadata.get("sectionTitle") or "").strip() or None

        return RagChunkHit(
            citationId=citation_id,
            sourceType=source_type,
            score=float(chunk.score),
            chunkText=chunk.text,
            documentId=document_id,
            workspaceId=workspace_id,
            userId=user_id,
            fileName=file_name,
            knowledgeDocumentId=knowledge_document_id,
            lawName=law_name,
            lawCode=law_code,
            legalDomain=legal_domain,
            pageNumber=int(page_number) if isinstance(page_number, int) or str(page_number).isdigit() else None,
            articleNumber=article_number,
            clauseNumber=clause_number,
            sectionTitle=section_title,
            rawChunkId=chunk.chunk_id,
            title=chunk.title,
            metadata=metadata,
            sourceChunk=chunk,
        )
