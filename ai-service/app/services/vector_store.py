from __future__ import annotations

from dataclasses import dataclass
from hashlib import md5
from pathlib import Path

from app.graph.repository import GraphRepository
from app.models.knowledge_models import ChunkedDocument, HierarchyNode
from app.schemas import ChunkRecord, DocumentProcessRequest
from app.services.document_embedding_service import HashingEmbeddingProvider


@dataclass(frozen=True)
class VectorStoreSaveResult:
    chunk_count: int


class VectorStoreService:
    def __init__(
        self,
        repository: GraphRepository | None = None,
        embedding_provider: HashingEmbeddingProvider | None = None,
    ) -> None:
        self.repository = repository or GraphRepository()
        self.embedding_provider = embedding_provider or HashingEmbeddingProvider()

    def save_mock_vector_records(
        self,
        request: DocumentProcessRequest,
        file_hash: str,
        chunks: list[ChunkRecord],
    ) -> VectorStoreSaveResult:
        document_id = request.documentId
        source_path = Path(request.filePath)
        file_type = request.fileType.lower().lstrip(".")
        title = request.fileName

        document_metadata = {
            "source_type": "USER_DOCUMENT",
            "job_id": request.jobId,
            "document_id": request.documentId,
            "workspace_id": request.workspaceId,
            "user_id": request.userId,
            "source_type": request.sourceType,
            "file_name": request.fileName,
            "file_hash": file_hash,
            "callback_url": request.callbackUrl,
        }

        nodes: list[HierarchyNode] = [
            HierarchyNode(
                node_id=document_id,
                label="Document",
                title=title,
                text=title,
                parent_id=None,
                order=0,
                token_count=len(title.split()),
                metadata=document_metadata,
            )
        ]

        chunk_nodes: list[HierarchyNode] = []
        embedding_texts = [chunk.chunk_text for chunk in chunks]
        embeddings = self.embedding_provider.embed(embedding_texts) if embedding_texts else []

        for index, chunk in enumerate(chunks, start=1):
            chunk_id = md5(
                f"{document_id}:{file_hash}:{index}:{chunk.chunk_text}".encode("utf-8"),
                usedforsecurity=False,
            ).hexdigest()
            metadata = {
                **document_metadata,
                "chunk_index": chunk.chunk_index,
                "page_number": chunk.page_number,
                "article_number": chunk.article_number,
                "clause_number": chunk.clause_number,
                "section_title": chunk.section_title,
                "unit_type": chunk.unit_type,
                "embedding_text": chunk.chunk_text,
                "retrieval_text": chunk.chunk_text,
                "file_path": request.filePath,
                "document_title": request.fileName,
                "chunk_id": chunk_id,
            }
            chunk_nodes.append(
                HierarchyNode(
                    node_id=chunk_id,
                    label="Chunk",
                    title=chunk.section_title or chunk.article_number or chunk.clause_number or request.fileName,
                    text=chunk.chunk_text,
                    parent_id=document_id,
                    order=chunk.chunk_index,
                    token_count=len(chunk.chunk_text.split()),
                    metadata=metadata,
                    embedding=embeddings[index - 1] if index - 1 < len(embeddings) else None,
                )
            )

        document = ChunkedDocument(
            document_id=document_id,
            title=title,
            source_path=source_path,
            file_type=file_type,
            nodes=[*nodes, *chunk_nodes],
            metadata=document_metadata,
        )
        self.repository.upsert_document(document)
        return VectorStoreSaveResult(chunk_count=len(chunk_nodes))
