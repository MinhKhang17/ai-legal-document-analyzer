from __future__ import annotations

from abc import ABC, abstractmethod
from pathlib import Path

from app.models.knowledge_models import ExtractedDocument


class DocumentLoader(ABC):
    supported_extensions: tuple[str, ...]

    @abstractmethod
    def load(self, source_path: Path) -> ExtractedDocument:
        ...