from __future__ import annotations

from app.services.document_embedding_service import HashingEmbeddingProvider


class RagEmbeddingService:
    def __init__(self) -> None:
        self._provider = HashingEmbeddingProvider()

    def embed(self, texts: list[str]) -> list[list[float]]:
        return self._provider.embed(texts)
