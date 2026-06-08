from __future__ import annotations

import os
import tempfile
from pathlib import Path

from fastapi import UploadFile

from app.graph.repository import GraphRepository
from app.models.knowledge_models import IngestionResult, RetrievedChunk
from app.services.document_embedding_service import HashingEmbeddingProvider
from app.services.document_loaders import build_default_loader_registry
from app.services.semantic_chunker import SemanticChunker


class KnowledgeService:
    def __init__(self) -> None:
        self.loaders = build_default_loader_registry()
        self.chunker = SemanticChunker()
        self.embedding_provider = HashingEmbeddingProvider()
        self.repository = GraphRepository()

    def supported_formats(self) -> list[str]:
        return self.loaders.supported_extensions()

    def ingest_file(
        self,
        source_path: str,
        title: str | None = None,
        *,
        filename: str | None = None,
    ) -> IngestionResult:
        path = Path(source_path)
        if not path.exists():
            raise FileNotFoundError(f"File not found: {source_path}")

        loader = self.loaders.resolve(path)
        extracted = loader.load(path)
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
        embeddings = self.embedding_provider.embed([node.text for node in chunk_nodes])

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
        )

    async def ingest_upload(self, file: UploadFile, title: str | None = None) -> IngestionResult:
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
            )
        finally:
            await file.close()
            if temp_path and temp_path.exists():
                try:
                    os.remove(temp_path)
                except OSError:
                    pass

    def search(self, query: str, top_k: int = 5) -> list[RetrievedChunk]:
        embedding = self.embedding_provider.embed([query])[0]
        return self.repository.search_chunks(embedding, top_k=top_k)
