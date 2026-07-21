"""Embedding service for text vectorization.

Fallback chain: SentenceTransformer (local) → Gemini Embedding API → Hashing Embedding.
"""
import logging
from typing import List

from app.config import settings
from app.services.document_embedding_service import HashingEmbeddingProvider

logger = logging.getLogger(__name__)


class EmbeddingService:
    """Service for generating text embeddings with multi-tier fallback."""

    def __init__(self):
        self.model = None
        self.model_name = settings.embedding_model
        self.fallback_provider = HashingEmbeddingProvider()
        self.gemini_provider = None
        self.using_fallback = True
        self.using_gemini = False

    def load_model(self):
        """Load the embedding model with fallback chain."""
        # Tier 1: Try loading local SentenceTransformer model
        try:
            logger.info(f"Loading embedding model: {self.model_name}")
            from sentence_transformers import SentenceTransformer

            self.model = SentenceTransformer(self.model_name)
            self.using_fallback = False
            self.using_gemini = False
            logger.info("Embedding model loaded successfully (using SentenceTransformer)")
            return
        except Exception as e:
            logger.warning(f"Failed to load SentenceTransformer model: {e}")
            self.model = None

        # Tier 2: Try Gemini Embedding API
        try:
            from app.services.gemini_embedding_provider import GeminiEmbeddingProvider

            gemini_provider = GeminiEmbeddingProvider()
            if gemini_provider.is_available:
                # Verify connectivity with a quick test
                test_result = gemini_provider.embed(["test"])
                if test_result and len(test_result[0]) > 0:
                    self.gemini_provider = gemini_provider
                    self.using_gemini = True
                    self.using_fallback = False
                    logger.info(
                        "Gemini Embedding API configured successfully (model=%s, dimensions=%s)",
                        gemini_provider.model,
                        len(test_result[0]),
                    )
                    return
                else:
                    logger.warning("Gemini Embedding API returned empty result, skipping")
            else:
                logger.info("Gemini Embedding API not configured (missing API key or model)")
        except Exception as e:
            logger.warning(f"Failed to initialize Gemini Embedding API: {e}")

        # Tier 3: Fall back to hashing embeddings
        self.using_fallback = True
        self.using_gemini = False
        logger.info("Using HashingEmbeddingProvider as final fallback")

    def embed_text(self, text: str) -> List[float]:
        """Generate embedding for a single text."""
        # Tier 1: Local SentenceTransformer
        if self.model and not self.using_fallback and not self.using_gemini:
            try:
                embedding = self.model.encode(text, convert_to_numpy=True)
                return embedding.tolist()
            except Exception as e:
                logger.error(f"SentenceTransformer embedding failed: {e}")
                raise

        # Tier 2: Gemini Embedding API
        if self.using_gemini and self.gemini_provider is not None:
            try:
                return self.gemini_provider.embed([text])[0]
            except Exception as e:
                logger.warning(f"Gemini Embedding API failed, falling back to hashing: {e}")
                return self.fallback_provider.embed([text])[0]

        # Tier 3: Hashing fallback
        return self.fallback_provider.embed([text])[0]

    def embed_texts(self, texts: List[str]) -> List[List[float]]:
        """Generate embeddings for multiple texts."""
        # Tier 1: Local SentenceTransformer
        if self.model and not self.using_fallback and not self.using_gemini:
            try:
                embeddings = self.model.encode(texts, convert_to_numpy=True)
                return [emb.tolist() for emb in embeddings]
            except Exception as e:
                logger.error(f"SentenceTransformer batch embedding failed: {e}")
                raise

        # Tier 2: Gemini Embedding API
        if self.using_gemini and self.gemini_provider is not None:
            try:
                return self.gemini_provider.embed(texts)
            except Exception as e:
                logger.warning(f"Gemini Embedding API batch failed, falling back to hashing: {e}")
                return self.fallback_provider.embed(texts)

        # Tier 3: Hashing fallback
        return self.fallback_provider.embed(texts)

    def embed_checklist_item(self, title: str, query_text: str, risk_question: str) -> List[float]:
        """Generate embedding for a checklist item by combining its components."""
        combined_text = f"{title}. {query_text}. {risk_question}"
        return self.embed_text(combined_text)


embedding_service = EmbeddingService()
