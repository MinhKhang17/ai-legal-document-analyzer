"""Embedding / similarity service.

Two backends behind one interface:

  * GeminiEmbeddingBackend - real semantic vectors via the Gemini
    `embedContent` API (model: gemini-embedding-001). Used automatically when a
    Gemini API key is configured. Embeddings are cached in-memory so repeated
    queries / static graph content are not re-embedded.
  * LexicalBackend - deterministic token-overlap similarity. Used when no key
    is available so the RAG API keeps working offline (no mock data).

`get_embedding_service()` picks the backend based on configuration. Callers use
`similarity(query, document)` and `rank(query, documents)` regardless of backend.
"""
from __future__ import annotations

import math
import re
import threading
import unicodedata
from typing import Dict, List, Optional, Sequence

import httpx

from app.core.config import LLMProvider, Settings, get_settings

_TOKEN_RE = re.compile(r"\w+", re.UNICODE)


# --- text helpers -------------------------------------------------------------


def _strip_accents(text: str) -> str:
    # Vietnamese "đ/Đ" is a distinct letter, not a combining diacritic, so it
    # survives NFD normalization. Map it explicitly so accent-insensitive
    # matching treats "đặt" and "dat" as equivalent.
    text = text.replace("đ", "d").replace("Đ", "D")
    nfkd = unicodedata.normalize("NFD", text)
    return "".join(c for c in nfkd if unicodedata.category(c) != "Mn")


def normalize(text: str) -> str:
    return _strip_accents(text.lower()).strip()


def tokenize(text: str) -> List[str]:
    return _TOKEN_RE.findall(normalize(text))


def _cosine(a: Sequence[float], b: Sequence[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)


# --- backends -----------------------------------------------------------------


class LexicalBackend:
    """Token-overlap similarity (no external calls)."""

    backend = "lexical"

    def similarity(self, query: str, document: str) -> float:
        q_tokens = set(tokenize(query))
        d_tokens = set(tokenize(document))
        if not q_tokens or not d_tokens:
            return 0.0
        overlap = q_tokens & d_tokens
        jaccard = len(overlap) / len(q_tokens | d_tokens)
        coverage = len(overlap) / len(q_tokens)
        phrase_boost = 0.4 if normalize(query) in normalize(document) else 0.0
        return round(min(1.0, 0.5 * jaccard + 0.3 * coverage + phrase_boost), 4)

    def rank(self, query: str, documents: List[str]) -> List[float]:
        return [self.similarity(query, d) for d in documents]


class GeminiEmbeddingBackend:
    """Semantic similarity via Gemini embeddings, with an in-memory cache.

    Falls back to lexical scoring transparently if an embedding call fails, so
    a transient API problem never breaks retrieval.
    """

    backend = "gemini"

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._api_key = settings.llm_api_key
        self._model = settings.embedding_model
        self._base = settings.gemini_base_url.rstrip("/")
        self._cache: Dict[str, List[float]] = {}
        self._lock = threading.Lock()
        self._lexical = LexicalBackend()

    def _embed(self, text: str) -> Optional[List[float]]:
        key = text.strip()
        if not key:
            return None
        with self._lock:
            if key in self._cache:
                return self._cache[key]
        url = f"{self._base}/v1beta/models/{self._model}:embedContent"
        headers = {"x-goog-api-key": self._api_key, "Content-Type": "application/json"}
        body = {
            "model": f"models/{self._model}",
            "content": {"parts": [{"text": key}]},
        }
        try:
            with httpx.Client(timeout=30.0) as client:
                resp = client.post(url, json=body, headers=headers)
                resp.raise_for_status()
                data = resp.json()
            values = data.get("embedding", {}).get("values")
            if not values:
                return None
            with self._lock:
                self._cache[key] = values
            return values
        except (httpx.HTTPError, KeyError, ValueError):
            return None  # caller falls back to lexical

    def _embed_batch(self, texts: List[str]) -> Dict[str, Optional[List[float]]]:
        """Embed several texts in one request (uncached ones only).

        Uses the batchEmbedContents endpoint. Falls back to per-item embedding
        (which itself falls back to lexical) if the batch call fails.
        """
        out: Dict[str, Optional[List[float]]] = {}
        to_fetch: List[str] = []
        for t in texts:
            key = t.strip()
            if not key:
                out[t] = None
                continue
            with self._lock:
                cached = self._cache.get(key)
            if cached is not None:
                out[t] = cached
            else:
                to_fetch.append(key)

        if not to_fetch:
            return out

        url = f"{self._base}/v1beta/models/{self._model}:batchEmbedContents"
        headers = {"x-goog-api-key": self._api_key, "Content-Type": "application/json"}
        body = {
            "requests": [
                {"model": f"models/{self._model}", "content": {"parts": [{"text": k}]}}
                for k in to_fetch
            ]
        }
        try:
            with httpx.Client(timeout=60.0) as client:
                resp = client.post(url, json=body, headers=headers)
                resp.raise_for_status()
                data = resp.json()
            embeddings = data.get("embeddings", [])
            for key, emb in zip(to_fetch, embeddings):
                values = emb.get("values")
                if values:
                    with self._lock:
                        self._cache[key] = values
                    out[key] = values
                else:
                    out[key] = None
            # Map back any keys the API omitted.
            for k in to_fetch:
                out.setdefault(k, None)
        except (httpx.HTTPError, KeyError, ValueError):
            # Batch failed: fall back to single embeds (each lexical-safe).
            for k in to_fetch:
                out[k] = self._embed(k)
        # Ensure result is keyed by the original text values too.
        result: Dict[str, Optional[List[float]]] = {}
        for t in texts:
            result[t] = out.get(t) if t in out else out.get(t.strip())
        return result

    def similarity(self, query: str, document: str) -> float:
        qv = self._embed(query)
        dv = self._embed(document)
        if qv is None or dv is None:
            return self._lexical.similarity(query, document)
        return round(max(0.0, _cosine(qv, dv)), 4)

    def rank(self, query: str, documents: List[str]) -> List[float]:
        qv = self._embed(query)
        if qv is None:
            return self._lexical.rank(query, documents)
        doc_vecs = self._embed_batch(documents)
        scores: List[float] = []
        for doc in documents:
            dv = doc_vecs.get(doc)
            if dv is None:
                scores.append(self._lexical.similarity(query, doc))
            else:
                scores.append(round(max(0.0, _cosine(qv, dv)), 4))
        return scores


# Public alias kept for backwards compatibility.
EmbeddingService = LexicalBackend


def _build_backend(settings: Settings):
    provider = (settings.embedding_provider or "AUTO").upper()
    has_gemini_key = bool(settings.llm_api_key)

    if provider == "LEXICAL":
        return LexicalBackend()
    if provider == "GEMINI":
        return GeminiEmbeddingBackend(settings) if has_gemini_key else LexicalBackend()
    # AUTO: prefer real Gemini embeddings whenever an API key is available.
    return GeminiEmbeddingBackend(settings) if has_gemini_key else LexicalBackend()


_service = None


def get_embedding_service():
    global _service
    if _service is None:
        _service = _build_backend(get_settings())
    return _service
