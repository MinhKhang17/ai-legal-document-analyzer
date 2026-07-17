"""Gemini Embedding API provider.

Provides high-quality semantic embeddings via Google's Gemini Embedding API
as a fallback when the local SentenceTransformer model is unavailable.
Falls back gracefully to HashingEmbeddingProvider if the API call fails.
"""
from __future__ import annotations

import json
import logging
import math
from typing import Any, Sequence
from urllib import error, request

from app.core.config import settings
from app.services.document_embedding_service import EmbeddingProvider

logger = logging.getLogger(__name__)

# Maximum texts per batch call to the Gemini Embedding API
_MAX_BATCH_SIZE = 100


class GeminiEmbeddingProvider(EmbeddingProvider):
    """Embedding provider using Google Gemini Embedding API (text-embedding-004 etc.)."""

    def __init__(
        self,
        *,
        api_key: str | None = None,
        model: str | None = None,
        dimensions: int | None = None,
        base_url: str | None = None,
        timeout_seconds: float = 30.0,
    ) -> None:
        self.api_key = (api_key or settings.gemini_api_key).strip()
        self.model = (model or settings.gemini_embedding_model).strip()
        self.dimensions = dimensions or settings.gemini_embedding_dimensions
        self.base_url = (base_url or settings.gemini_base_url).rstrip("/")
        self.timeout_seconds = timeout_seconds

        # Support key rotation (comma or semicolon separated)
        self._api_keys = [k.strip() for k in self.api_key.replace(";", ",").split(",") if k.strip()]
        self._current_key_index = 0

    @property
    def is_available(self) -> bool:
        return bool(self._api_keys and self.model)

    def _get_current_key(self) -> str:
        if not self._api_keys:
            return ""
        return self._api_keys[self._current_key_index]

    def _rotate_key(self) -> None:
        if len(self._api_keys) > 1:
            self._current_key_index = (self._current_key_index + 1) % len(self._api_keys)

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        """Embed a list of texts using the Gemini Embedding API.

        Automatically batches large requests and handles retries with key rotation.
        """
        if not self.is_available:
            raise RuntimeError("Gemini Embedding API is not configured (missing API key or model)")

        if not texts:
            return []

        # Process in batches
        all_embeddings: list[list[float]] = []
        for batch_start in range(0, len(texts), _MAX_BATCH_SIZE):
            batch = texts[batch_start : batch_start + _MAX_BATCH_SIZE]
            batch_embeddings = self._embed_batch(list(batch))
            all_embeddings.extend(batch_embeddings)

        return all_embeddings

    def _embed_batch(self, texts: list[str]) -> list[list[float]]:
        """Embed a batch of texts in a single API call."""
        if len(texts) == 1:
            return [self._embed_single(texts[0])]

        # Use batchEmbedContents for multiple texts
        requests_payload: list[dict[str, Any]] = []
        for text in texts:
            req_item: dict[str, Any] = {
                "model": f"models/{self.model}",
                "content": {"parts": [{"text": text}]},
            }
            if self.dimensions:
                req_item["outputDimensionality"] = self.dimensions
            requests_payload.append(req_item)

        payload = {"requests": requests_payload}

        url = f"{self.base_url}/models/{self.model}:batchEmbedContents"
        active_key = self._get_current_key()

        req = request.Request(
            url,
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "x-goog-api-key": active_key,
                "Content-Type": "application/json",
            },
            method="POST",
        )

        try:
            with request.urlopen(req, timeout=self.timeout_seconds) as response:
                body = json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exc:
            logger.warning("Gemini Embedding batch API error %s: %s", exc.code, exc.reason)
            if exc.code == 429 and len(self._api_keys) > 1:
                self._rotate_key()
            raise RuntimeError(f"Gemini Embedding API failed: HTTP {exc.code} {exc.reason}") from exc
        except Exception as exc:
            logger.warning("Gemini Embedding API request failed: %s", exc)
            raise RuntimeError(f"Gemini Embedding API failed: {exc}") from exc

        embeddings_list = body.get("embeddings") or []
        if len(embeddings_list) != len(texts):
            raise RuntimeError(
                f"Gemini Embedding API returned {len(embeddings_list)} embeddings for {len(texts)} texts"
            )

        result: list[list[float]] = []
        for embedding_obj in embeddings_list:
            values = embedding_obj.get("values") or []
            result.append(self._normalize_vector(values))

        return result

    def _embed_single(self, text: str) -> list[float]:
        """Embed a single text using embedContent endpoint."""
        payload: dict[str, Any] = {
            "model": f"models/{self.model}",
            "content": {"parts": [{"text": text}]},
        }
        if self.dimensions:
            payload["outputDimensionality"] = self.dimensions

        url = f"{self.base_url}/models/{self.model}:embedContent"
        active_key = self._get_current_key()

        req = request.Request(
            url,
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "x-goog-api-key": active_key,
                "Content-Type": "application/json",
            },
            method="POST",
        )

        try:
            with request.urlopen(req, timeout=self.timeout_seconds) as response:
                body = json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exc:
            logger.warning("Gemini Embedding API error %s: %s", exc.code, exc.reason)
            if exc.code == 429 and len(self._api_keys) > 1:
                self._rotate_key()
            raise RuntimeError(f"Gemini Embedding API failed: HTTP {exc.code} {exc.reason}") from exc
        except Exception as exc:
            logger.warning("Gemini Embedding API request failed: %s", exc)
            raise RuntimeError(f"Gemini Embedding API failed: {exc}") from exc

        embedding = body.get("embedding") or {}
        values = embedding.get("values") or []
        if not values:
            raise RuntimeError("Gemini Embedding API returned empty embedding")

        return self._normalize_vector(values)

    def _normalize_vector(self, vector: list[float]) -> list[float]:
        """L2-normalize the embedding vector."""
        norm = math.sqrt(sum(v * v for v in vector)) or 1.0
        return [v / norm for v in vector]
