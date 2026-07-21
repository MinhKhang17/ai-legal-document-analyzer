"""LLM-based reranker using Gemini as a cross-encoder substitute.

Sends the user's query along with candidate chunks to Gemini,
which scores each chunk's relevance on a 0-10 scale.
Falls back to the original heuristic reranking on any failure.
"""
from __future__ import annotations

import json
import logging
from typing import Sequence

from app.core.config import settings
from app.models.knowledge_models import RetrievedChunk
from app.services.gemini_client import GeminiClient

logger = logging.getLogger(__name__)

_RERANK_SYSTEM_PROMPT = """You are a legal document relevance scorer for Vietnamese law.

Given a user query and a list of document chunks, score each chunk's relevance to the query on a scale of 0-10:
- 10: Perfectly relevant, directly answers the query
- 7-9: Highly relevant, contains key information
- 4-6: Somewhat relevant, contains related information
- 1-3: Marginally relevant, only loosely connected
- 0: Completely irrelevant

Return ONLY valid JSON array with chunk indices and scores (no markdown, no explanation):
[{"index": 0, "score": 8.5}, {"index": 1, "score": 3.0}, ...]"""


class LlmReranker:
    """Reranker using Gemini LLM to score chunk relevance."""

    def __init__(
        self,
        *,
        api_key: str | None = None,
        model: str | None = None,
        max_chunks_to_rerank: int = 10,
    ) -> None:
        self.api_key = api_key or settings.gemini_api_key
        self.model = model or settings.gemini_model
        self.max_chunks_to_rerank = max_chunks_to_rerank

    def rerank(
        self,
        query: str,
        chunks: Sequence[RetrievedChunk],
    ) -> list[RetrievedChunk]:
        """Rerank chunks using LLM relevance scoring.

        Returns reranked list sorted by LLM score (descending).
        On any failure, returns the original list unchanged.
        """
        if not chunks or not self.api_key or not self.model:
            return list(chunks)

        # Limit the number of chunks sent to LLM for cost/speed
        candidates = list(chunks[: self.max_chunks_to_rerank])
        remaining = list(chunks[self.max_chunks_to_rerank :])

        try:
            scores = self._score_chunks(query, candidates)
        except Exception as exc:
            logger.warning("LLM reranking failed, returning original order: %s", exc)
            return list(chunks)

        if not scores or len(scores) != len(candidates):
            logger.debug("LLM reranker returned mismatched scores, returning original order")
            return list(chunks)

        # Pair chunks with LLM scores and sort
        scored_chunks = list(zip(scores, candidates))
        scored_chunks.sort(key=lambda item: item[0], reverse=True)

        reranked = [chunk for _, chunk in scored_chunks]

        # Append any remaining chunks that weren't scored
        reranked.extend(remaining)

        logger.info(
            "LLM reranked %d chunks (top score=%.1f, bottom score=%.1f)",
            len(candidates),
            scored_chunks[0][0] if scored_chunks else 0.0,
            scored_chunks[-1][0] if scored_chunks else 0.0,
        )

        return reranked

    def _score_chunks(self, query: str, chunks: list[RetrievedChunk]) -> list[float]:
        """Call Gemini to score chunk relevance."""
        # Build compact representations of each chunk
        chunk_descriptions: list[str] = []
        for i, chunk in enumerate(chunks):
            text = " ".join(chunk.text.split()).strip()
            if len(text) > 300:
                text = text[:300].rsplit(" ", 1)[0].strip() + "..."
            title = " ".join((chunk.title or "").split()).strip()
            chunk_descriptions.append(f"[{i}] Title: {title}\nText: {text}")

        user_prompt = (
            f"Query: {query}\n\n"
            f"Chunks to score:\n"
            + "\n\n".join(chunk_descriptions)
        )

        client = GeminiClient(
            api_key=self.api_key,
            model=self.model,
            base_url=settings.gemini_base_url,
            timeout_seconds=min(settings.gemini_timeout_seconds, 20.0),
            max_output_tokens=256,
            max_retries=1,
            retry_backoff_seconds=0.5,
        )

        result = client.generate_text(
            system_prompt=_RERANK_SYSTEM_PROMPT,
            user_prompt=user_prompt,
        )

        if result.error or not result.text:
            raise RuntimeError(f"LLM reranking call failed: {result.error}")

        return self._parse_scores(result.text, expected_count=len(chunks))

    def _parse_scores(self, text: str, expected_count: int) -> list[float]:
        """Parse the JSON array of scores from the LLM response."""
        clean = text.strip()
        if clean.startswith("```"):
            lines = clean.splitlines()
            if lines[0].startswith("```"):
                lines = lines[1:]
            if lines and lines[-1].startswith("```"):
                lines = lines[:-1]
            clean = "\n".join(lines).strip()

        parsed = json.loads(clean)
        if not isinstance(parsed, list):
            raise ValueError(f"Expected JSON array, got {type(parsed).__name__}")

        # Build a mapping from index → score
        score_map: dict[int, float] = {}
        for item in parsed:
            if isinstance(item, dict) and "index" in item and "score" in item:
                score_map[int(item["index"])] = float(item["score"])

        # Return scores in order, defaulting to 0.0 for missing indices
        return [score_map.get(i, 0.0) for i in range(expected_count)]
