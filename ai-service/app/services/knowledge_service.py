from __future__ import annotations

import os
import tempfile
from pathlib import Path
from typing import Any

from fastapi import UploadFile

from app.graph.repository import GraphRepository
from app.models.knowledge_models import IngestionResult, RetrievedChunk
from app.services.document_embedding_service import HashingEmbeddingProvider
from app.services.loader.document_loaders import build_default_loader_registry
from app.services.semantic_chunker import SemanticChunker, SemanticChunkerV2


class KnowledgeService:
    def __init__(
        self,
        *,
        repository: GraphRepository | None = None,
        document_metadata: dict[str, Any] | None = None,
        chunker: SemanticChunker | None = None,
    ) -> None:
        self.loaders = build_default_loader_registry()
        self.chunker = chunker or SemanticChunker()
        self.embedding_provider = HashingEmbeddingProvider()
        self.repository = repository or GraphRepository()
        self.document_metadata = document_metadata or {}

    def supported_formats(self) -> list[str]:
        return self.loaders.supported_extensions()

    def ingest_file(
        self,
        source_path: str,
        title: str | None = None,
        *,
        filename: str | None = None,
        ingestion_version: int = 1,
    ) -> IngestionResult:
        path = Path(source_path)
        if not path.exists():
            raise FileNotFoundError(f"File not found: {source_path}")

        loader = self.loaders.resolve(path)
        extracted = loader.load(path)
        if self.document_metadata:
            extracted = extracted.__class__(
                source_path=extracted.source_path,
                title=extracted.title,
                file_type=extracted.file_type,
                blocks=extracted.blocks,
                metadata={**extracted.metadata, **self.document_metadata},
            )
        if title and title.strip():
            extracted = extracted.__class__(
                source_path=extracted.source_path,
                title=title.strip(),
                file_type=extracted.file_type,
                blocks=extracted.blocks,
                metadata=extracted.metadata,
            )
        chunked = self.chunker.chunk_document(extracted)

        chunk_nodes = [node for node in chunked.nodes if node.label == "Chunk"]
        embedding_texts = [
            str(node.metadata.get("embedding_text") or node.text)
            for node in chunk_nodes
        ]
        embeddings = self.embedding_provider.embed(embedding_texts)

        enriched_nodes = []
        embedding_index = 0
        for node in chunked.nodes:
            if node.label == "Chunk":
                enriched_nodes.append(
                    node.__class__(
                        node_id=node.node_id,
                        label=node.label,
                        title=node.title,
                        text=node.text,
                        parent_id=node.parent_id,
                        order=node.order,
                        token_count=node.token_count,
                        metadata=node.metadata,
                        embedding=embeddings[embedding_index],
                    )
                )
                embedding_index += 1
            else:
                enriched_nodes.append(node)

        chunked = chunked.__class__(
            document_id=chunked.document_id,
            title=chunked.title,
            source_path=chunked.source_path,
            file_type=chunked.file_type,
            nodes=enriched_nodes,
            metadata=chunked.metadata,
        )

        self.repository.upsert_document(chunked)

        chunk_ids = [node.node_id for node in enriched_nodes if node.label == "Chunk"]
        return IngestionResult(
            document_id=chunked.document_id,
            title=chunked.title,
            file_type=chunked.file_type,
            source_path=str(chunked.source_path),
            total_blocks=len(extracted.blocks),
            total_units=int(chunked.metadata.get("total_units", 0)),
            total_chunks=len(chunk_ids),
            chunk_ids=chunk_ids,
            filename=filename or path.name,
            ingestion_version=ingestion_version,
            chunking_strategy=chunked.metadata.get("chunking_strategy", self.chunker.__class__.__name__),
        )

    async def ingest_upload(
        self,
        file: UploadFile,
        title: str | None = None,
        *,
        ingestion_version: int = 1,
    ) -> IngestionResult:
        suffix = Path(file.filename or "").suffix.lower()
        if suffix not in self.supported_formats():
            raise ValueError(f"Unsupported file type: {suffix or '[unknown]'}")

        temp_path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
                temp_path = Path(temp_file.name)
                while True:
                    chunk = await file.read(1024 * 1024)
                    if not chunk:
                        break
                    temp_file.write(chunk)

            return self.ingest_file(
                str(temp_path),
                title=title or Path(file.filename or "").stem,
                filename=file.filename,
                ingestion_version=ingestion_version,
            )
        finally:
            await file.close()
            if temp_path and temp_path.exists():
                try:
                    os.remove(temp_path)
                except OSError:
                    pass

    def search(
        self,
        query: str,
        top_k: int = 5,
        *,
        metadata_filter: dict[str, Any] | None = None,
    ) -> list[RetrievedChunk]:
        embedding = self.embedding_provider.embed([query])[0]
        return self.repository.search_chunks(
            embedding,
            top_k=top_k,
            query_text=query,
            metadata_filter=metadata_filter,
        )


class KnowledgeServiceV2(KnowledgeService):
    def __init__(
        self,
        *,
        repository: GraphRepository | None = None,
        document_metadata: dict[str, Any] | None = None,
    ) -> None:
        merged_metadata = {
            "ingestion_version": 2,
            "chunking_strategy": "structural_semantic_subchunk_v2",
        }
        if document_metadata:
            merged_metadata.update(document_metadata)
        super().__init__(
            repository=repository,
            document_metadata=merged_metadata,
            chunker=SemanticChunkerV2(),
        )

    def search(self, query: str, top_k: int = 5) -> list[RetrievedChunk]:
        return super().search(query, top_k=top_k, metadata_filter={"chunking_version": 2})
