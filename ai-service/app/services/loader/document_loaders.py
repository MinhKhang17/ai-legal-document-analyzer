from __future__ import annotations

import re
from pathlib import Path
from typing import TYPE_CHECKING

from app.models.knowledge_models import DocumentBlock, ExtractedDocument

if TYPE_CHECKING:
    from app.services.loader.base_loader import DocumentLoader


LEGAL_SECTION_PATTERNS = (
    re.compile(r"^\s*Chương\s+[IVXLC\d]+(?:\s+.*)?$", re.IGNORECASE),
    re.compile(r"^\s*Mục\s+\d+(?:\s+.*)?$", re.IGNORECASE),
    re.compile(r"^\s*Điều\s+\d+(?:[.\-:]\s*.*)?$", re.IGNORECASE),
    re.compile(r"^\s*Khoản\s+\d+(?:[.\-:]\s*.*)?$", re.IGNORECASE),
    re.compile(r"^\s*Điểm\s+[a-zA-Z0-9]+(?:[.\-:]\s*.*)?$", re.IGNORECASE),
)

QUESTION_PATTERNS = (
    re.compile(r"^\s*(Q(?:uestion)?|Câu hỏi)\s*[:\-]", re.IGNORECASE),
    re.compile(r".+\?\s*$"),
)

LEGAL_BOUNDARY_PATTERN = re.compile(
    r"(?:Chương\s+[IVXLC\d]+|Mục\s+\d+|Điều\s+\d+|Khoản\s+\d+|Điểm\s+[a-zA-Z0-9]+)",
    re.IGNORECASE,
)


def normalize_text(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def split_merged_legal_line(line: str) -> list[str]:
    matches = list(LEGAL_BOUNDARY_PATTERN.finditer(line))
    if len(matches) <= 1:
        return [line]

    pieces: list[str] = []
    prefix = line[: matches[0].start()].strip()
    if prefix:
        pieces.append(prefix)

    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(line)
        piece = line[start:end].strip()
        if piece:
            pieces.append(piece)

    return pieces


def group_lines_into_blocks(lines: list[str]) -> list[str]:
    blocks: list[str] = []
    current: list[str] = []

    for raw_line in lines:
        line = raw_line.strip()
        if not line:
            if current:
                blocks.append(normalize_text(" ".join(current)))
                current = []
            continue

        expanded_lines = split_merged_legal_line(line)
        for expanded_line in expanded_lines:
            expanded_line = expanded_line.strip()
            if not expanded_line:
                continue
            if current and LEGAL_BOUNDARY_PATTERN.match(expanded_line):
                blocks.append(normalize_text(" ".join(current)))
                current = [expanded_line]
                continue
            current.append(expanded_line)

    if current:
        blocks.append(normalize_text(" ".join(current)))

    return [block for block in blocks if block]


def is_heading_like(text: str) -> bool:
    if any(pattern.match(text) for pattern in LEGAL_SECTION_PATTERNS):
        return True
    return text.isupper() and len(text) < 250 and len(text.split()) <= 30


def looks_like_question(text: str) -> bool:
    return any(pattern.match(text) for pattern in QUESTION_PATTERNS)


class LoaderRegistry:
    """Registry mapping file extensions to document loaders."""

    def __init__(self, loaders: list[DocumentLoader]) -> None:
        self._loaders: dict[str, DocumentLoader] = {}
        for loader in loaders:
            for ext in loader.supported_extensions:
                self._loaders[ext.lower()] = loader

    def supported_extensions(self) -> list[str]:
        return sorted(self._loaders.keys())

    def resolve(self, path: Path) -> DocumentLoader:
        loader = self._loaders.get(path.suffix.lower())
        if loader is None:
            supported = ", ".join(self._loaders.keys())
            raise ValueError(
                f"No loader found for extension '{path.suffix.lower()}'. Supported: {supported}"
            )
        return loader


def build_default_loader_registry() -> LoaderRegistry:
    from app.services.loader.PdfDocumentLoader import PdfDocumentLoader
    from app.services.loader.word_document_loader import WordDocumentLoader
    from app.services.loader.text_document_loader import TextDocumentLoader

    return LoaderRegistry(
        loaders=[
            PdfDocumentLoader(),
            TextDocumentLoader(),
            WordDocumentLoader(),
        ]
    )
