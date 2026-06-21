"""Embedding service for text vectorization."""
import logging
from typing import List

from app.config import settings
from app.services.document_embedding_service import HashingEmbeddingProvider

logger = logging.getLogger(__name__)


class EmbeddingService:
    """Service for generating text embeddings."""

    def __init__(self):
        self.model = None
        self.model_name = settings.embedding_model
        self.fallback_provider = HashingEmbeddingProvider()
        self.using_fallback = True

    def load_model(self):
        """Load the embedding model."""
        try:
            logger.info(f"Loading embedding model: {self.model_name}")
            from sentence_transformers import SentenceTransformer

            self.model = SentenceTransformer(self.model_name)
            self.using_fallback = False
            logger.info("Embedding model loaded successfully")
            return
        except Exception as e:
            logger.warning(f"Failed to load embedding model, falling back to hashing embeddings: {e}")
            self.model = None
            self.using_fallback = True

    def embed_text(self, text: str) -> List[float]:
        """Generate embedding for a single text."""
        if self.model and not self.using_fallback:
            try:
                embedding = self.model.encode(text, convert_to_numpy=True)
                return embedding.tolist()
            except Exception as e:
                logger.error(f"Failed to generate embedding: {e}")
                raise

        return self.fallback_provider.embed([text])[0]

    def embed_texts(self, texts: List[str]) -> List[List[float]]:
        """Generate embeddings for multiple texts."""
        if self.model and not self.using_fallback:
            try:
                embeddings = self.model.encode(texts, convert_to_numpy=True)
                return [emb.tolist() for emb in embeddings]
            except Exception as e:
                logger.error(f"Failed to generate embeddings: {e}")
                raise

        return self.fallback_provider.embed(texts)

    def embed_checklist_item(self, title: str, query_text: str, risk_question: str) -> List[float]:
        """Generate embedding for a checklist item by combining its components."""
        combined_text = f"{title}. {query_text}. {risk_question}"
        return self.embed_text(combined_text)


embedding_service = EmbeddingService()
