from __future__ import annotations

import hashlib
import math
import re
from abc import ABC, abstractmethod
from typing import Sequence

from app.core.config import settings


class EmbeddingProvider(ABC):
    @abstractmethod
    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        raise NotImplementedError


class HashingEmbeddingProvider(EmbeddingProvider):
    def __init__(self, dimensions: int | None = None) -> None:
        self.dimensions = dimensions or settings.embedding_dimensions

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        return [self._embed_one(text) for text in texts]

    def _embed_one(self, text: str) -> list[float]:
        vector = [0.0] * self.dimensions
        tokens = re.findall(r"\w+", text.lower())
        if not tokens:
            return vector

        for token in tokens:
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            for index in range(0, len(digest), 4):
                slot = int.from_bytes(digest[index : index + 4], "big") % self.dimensions
                vector[slot] += 1.0

        norm = math.sqrt(sum(value * value for value in vector)) or 1.0
        return [value / norm for value in vector]
