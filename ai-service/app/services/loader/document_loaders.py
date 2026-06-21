from __future__ import annotations

import re
import unicodedata
from pathlib import Path
from typing import TYPE_CHECKING

from app.models.knowledge_models import DocumentBlock, ExtractedDocument

if TYPE_CHECKING:
    from app.services.loader.base_loader import DocumentLoader


LEGAL_SECTION_PATTERNS = (
    re.compile(r"^\s*chuong\s+[ivxlcd\d]+(?:\s+.*)?$", re.IGNORECASE),
    re.compile(r"^\s*muc\s+\d+(?:\s+.*)?$", re.IGNORECASE),
    re.compile(r"^\s*dieu\s+\d+(?:[.\-:]\s*.*)?$", re.IGNORECASE),
    re.compile(r"^\s*khoan\s+\d+(?:[.\-:]\s*.*)?$", re.IGNORECASE),
    re.compile(r"^\s*diem\s+[a-z0-9]+(?:[.\-:]\s*.*)?$", re.IGNORECASE),
)

QUESTION_PATTERNS = (
    re.compile(r"^\s*(q(?:uestion)?|cau hoi)\s*[:\-]", re.IGNORECASE),
    re.compile(r".+\?\s*$"),
)

LEGAL_BOUNDARY_PATTERN = re.compile(
    r"(?:chuong\s+[ivxlcd\d]+|muc\s+\d+|dieu\s+\d+|khoan\s+\d+|diem\s+[a-z0-9]+)",
    re.IGNORECASE,
)

HEADER_FOOTER_PATTERNS = (
    re.compile(r"^cong bao\s*/\s*so\b", re.IGNORECASE),
    re.compile(r"^cong hoa xa hoi chu nghia viet nam\b", re.IGNORECASE),
    re.compile(r"^doc lap\s*-\s*tu do\s*-\s*hanh phuc\b", re.IGNORECASE),
    re.compile(r"^loai tai lieu\b", re.IGNORECASE),
    re.compile(r"^vi tri\b", re.IGNORECASE),
    re.compile(r"^tieu de\b", re.IGNORECASE),
    re.compile(r"^noi dung\b", re.IGNORECASE),
    re.compile(r"^trang\s+\d+(\s*/\s*\d+)?$", re.IGNORECASE),
    re.compile(r"^page\s+\d+(\s*/\s*\d+)?$", re.IGNORECASE),
)


def _fold(text: str) -> str:
    normalized = unicodedata.normalize("NFKD", text)
    stripped = "".join(char for char in normalized if not unicodedata.combining(char))
    return re.sub(r"\s+", " ", stripped).strip().lower()


def _fold_with_map(text: str) -> tuple[str, list[int]]:
    folded_chars: list[str] = []
    index_map: list[int] = []
    for index, char in enumerate(text):
        normalized = unicodedata.normalize("NFKD", char)
        for part in normalized:
            if unicodedata.combining(part):
                continue
            folded_chars.append(part.lower())
            index_map.append(index)
    folded = re.sub(r"\s+", " ", "".join(folded_chars)).strip()
    return folded, index_map


def normalize_text(text: str) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    text = re.sub(r"\s+([,.;:!?])", r"\1", text)
    text = re.sub(r"([(\[])\s+", r"\1", text)
    text = re.sub(r"\s+([)\]])", r"\1", text)
    return text


def _is_meta_or_header(line: str) -> bool:
    folded = _fold(line)
    return any(pattern.match(folded) for pattern in HEADER_FOOTER_PATTERNS)


def split_merged_legal_line(line: str) -> list[str]:
    folded, index_map = _fold_with_map(line)
    matches = list(LEGAL_BOUNDARY_PATTERN.finditer(folded))
    if len(matches) <= 1:
        return [line]

    pieces: list[str] = []
    prefix = line[: index_map[matches[0].start()]].strip()
    if prefix:
        pieces.append(prefix)

    for index, match in enumerate(matches):
        start = index_map[match.start()]
        end = index_map[matches[index + 1].start()] if index + 1 < len(matches) else len(line)
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

        if _is_meta_or_header(line):
            if current:
                blocks.append(normalize_text(" ".join(current)))
                current = []
            continue

        expanded_lines = split_merged_legal_line(line)
        for expanded_line in expanded_lines:
            expanded_line = expanded_line.strip()
            if not expanded_line or _is_meta_or_header(expanded_line):
                if current:
                    blocks.append(normalize_text(" ".join(current)))
                    current = []
                continue
            if current and LEGAL_BOUNDARY_PATTERN.match(_fold(expanded_line)):
                blocks.append(normalize_text(" ".join(current)))
                current = [expanded_line]
                continue
            current.append(expanded_line)

    if current:
        blocks.append(normalize_text(" ".join(current)))

    return [block for block in blocks if block]


def is_heading_like(text: str) -> bool:
    folded = _fold(text)
    if any(pattern.match(folded) for pattern in LEGAL_SECTION_PATTERNS):
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
    from app.services.loader.doc_document_loader import DocDocumentLoader
    from app.services.loader.word_document_loader import WordDocumentLoader
    from app.services.loader.text_document_loader import TextDocumentLoader

    return LoaderRegistry(
        loaders=[
            PdfDocumentLoader(),
            DocDocumentLoader(),
            TextDocumentLoader(),
            WordDocumentLoader(),
        ]
    )
