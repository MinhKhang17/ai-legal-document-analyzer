from __future__ import annotations

import re
from dataclasses import dataclass
from hashlib import md5
from pathlib import Path

from app.core.config import settings
from app.models.knowledge_models import (
    ChunkedDocument,
    DocumentBlock,
    ExtractedDocument,
    HierarchyNode,
)


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
def _count_tokens(text: str) -> int:
    return max(1, len(re.findall(r"\S+", text)))


def _normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _is_heading(block: DocumentBlock) -> bool:
    if block.kind == "heading":
        return True
    if block.style and re.match(r"Heading\s+\d+", block.style, flags=re.IGNORECASE):
        return True
    return any(pattern.match(block.text) for pattern in LEGAL_SECTION_PATTERNS)


def _legal_heading_level(text: str) -> int:
    for level, pattern in enumerate(LEGAL_SECTION_PATTERNS, start=1):
        if pattern.match(text):
            return level
    return 0


def _looks_like_question(text: str) -> bool:
    return any(pattern.match(text) for pattern in QUESTION_PATTERNS)


def _split_text_recursive(text: str, hard_max_tokens: int) -> list[str]:
    normalized = _normalize(text)
    if _count_tokens(normalized) <= hard_max_tokens:
        return [normalized]

    paragraph_parts = [part.strip() for part in re.split(r"\n\s*\n", text) if part.strip()]
    if len(paragraph_parts) > 1:
        pieces: list[str] = []
        for part in paragraph_parts:
            pieces.extend(_split_text_recursive(part, hard_max_tokens))
        return pieces

    sentence_parts = [part.strip() for part in re.split(r"(?<=[.!?ã€‚ï¼ï¼Ÿ])\s+", text) if part.strip()]
    if len(sentence_parts) > 1:
        pieces = []
        for part in sentence_parts:
            pieces.extend(_split_text_recursive(part, hard_max_tokens))
        return pieces

    words = normalized.split()
    if not words:
        return []

    pieces = []
    start = 0
    step = max(1, hard_max_tokens)
    while start < len(words):
        pieces.append(" ".join(words[start : start + step]))
        start += step
    return pieces


@dataclass(frozen=True)
class _UnitGroup:
    hierarchy_path: list[str]
    unit_type: str
    title: str
    text: str
    source_indexes: list[int]
    order: int

    @property
    def token_count(self) -> int:
        return _count_tokens(self.text)


class SemanticChunker:
    def __init__(self) -> None:
        self.target_min_tokens = settings.chunk_target_min_tokens
        self.target_max_tokens = settings.chunk_target_max_tokens
        self.hard_max_tokens = settings.chunk_hard_max_tokens

    def chunk_document(self, document: ExtractedDocument) -> ChunkedDocument:
        profile = self._infer_profile(document.blocks)
        units = self._build_units(document.blocks, profile)
        if not units:
            fallback_text = "\n\n".join(_normalize(block.text) for block in document.blocks if _normalize(block.text))
            if fallback_text:
                units = [
                    _UnitGroup(
                        hierarchy_path=["Document"],
                        unit_type="generic",
                        title=document.title,
                        text=fallback_text,
                        source_indexes=[block.order for block in document.blocks if _normalize(block.text)],
                        order=1,
                    )
                ]
        chunks = self._pack_units(document, units)
        return ChunkedDocument(
            document_id=self._build_id(document.source_path, "document"),
            title=document.title,
            source_path=document.source_path,
            file_type=document.file_type,
            nodes=self._build_nodes(document, units, chunks, profile),
            metadata={
                "profile": profile,
                "source_metadata": document.metadata,
                "total_units": len(units),
                "total_chunks": len(chunks),
            },
        )

    def _infer_profile(self, blocks: list[DocumentBlock]) -> str:
        if not blocks:
            return "generic"

        legal_hits = sum(1 for block in blocks if any(pattern.match(block.text) for pattern in LEGAL_SECTION_PATTERNS))
        question_hits = sum(1 for block in blocks if _looks_like_question(block.text))
        heading_hits = sum(1 for block in blocks if _is_heading(block))

        if question_hits >= max(2, len(blocks) // 8):
            return "faq"
        if legal_hits >= max(2, len(blocks) // 10):
            return "legal"
        if heading_hits >= max(2, len(blocks) // 6):
            return "report"
        return "generic"

    def _build_units(self, blocks: list[DocumentBlock], profile: str) -> list[_UnitGroup]:
        if profile == "faq":
            return self._build_faq_units(blocks)
        return self._build_sectioned_units(blocks, profile)

    def _build_faq_units(self, blocks: list[DocumentBlock]) -> list[_UnitGroup]:
        units: list[_UnitGroup] = []
        current_question: DocumentBlock | None = None
        current_answer_parts: list[str] = []
        current_indexes: list[int] = []

        def flush() -> None:
            nonlocal current_question, current_answer_parts, current_indexes
            if current_question is None:
                return
            answer_text = _normalize(" ".join(current_answer_parts))
            question_text = _normalize(current_question.text)
            combined = question_text if not answer_text else f"{question_text}\n\n{answer_text}"
            units.append(
                _UnitGroup(
                    hierarchy_path=[question_text, "Answer"],
                    unit_type="faq",
                    title=question_text,
                    text=combined,
                    source_indexes=list(current_indexes),
                    order=len(units) + 1,
                )
            )
            current_question = None
            current_answer_parts = []
            current_indexes = []

        for index, block in enumerate(blocks):
            if _looks_like_question(block.text):
                flush()
                current_question = block
                current_indexes = [index]
                continue
            if current_question is not None:
                current_answer_parts.append(block.text)
                current_indexes.append(index)
            elif block.text:
                units.append(
                    _UnitGroup(
                        hierarchy_path=["FAQ"],
                        unit_type="faq",
                        title=block.text[:120],
                        text=block.text,
                        source_indexes=[index],
                        order=len(units) + 1,
                    )
                )

        flush()
        return units

    def _build_sectioned_units(self, blocks: list[DocumentBlock], profile: str) -> list[_UnitGroup]:
        units: list[_UnitGroup] = []
        section_stack: list[str] = []
        pending_title: str | None = None

        for index, block in enumerate(blocks):
            if _is_heading(block):
                heading_text = _normalize(block.text)
                if profile == "legal":
                    level = _legal_heading_level(heading_text)
                    if level:
                        section_stack = section_stack[: level - 1]
                        section_stack.append(heading_text)
                    else:
                        section_stack = section_stack[:1]
                        section_stack.append(heading_text)
                else:
                    level = block.heading_level or self._detect_report_heading_level(heading_text)
                    if level:
                        section_stack = section_stack[: level - 1]
                        section_stack.append(heading_text)
                    else:
                        section_stack = section_stack[:1]
                        section_stack.append(heading_text)

                pending_title = heading_text
                continue

            if not block.text:
                continue

            if profile == "generic" and not section_stack:
                section_stack = ["Document"]

            hierarchy_path = section_stack[:] or ["Document"]
            units.append(
                _UnitGroup(
                    hierarchy_path=hierarchy_path,
                    unit_type=profile,
                    title=pending_title or block.text[:120],
                    text=block.text,
                    source_indexes=[index],
                    order=len(units) + 1,
                )
            )
            pending_title = None

        return units

    def _detect_report_heading_level(self, text: str) -> int:
        if re.match(r"^\d+(\.\d+)*\s+\S+", text):
            return text.count(".") + 1
        if len(text) <= 80 and text == text.upper():
            return 1
        if text.endswith(":") and len(text.split()) <= 12:
            return 2
        return 0

    def _pack_units(self, document: ExtractedDocument, units: list[_UnitGroup]) -> list[dict[str, object]]:
        packed: list[dict[str, object]] = []
        current_units: list[_UnitGroup] = []
        current_tokens = 0

        def flush_current() -> None:
            nonlocal current_units, current_tokens
            if not current_units:
                return
            packed.append(self._make_chunk(document, current_units, len(packed) + 1))
            current_units = []
            current_tokens = 0

        for unit in units:
            if unit.token_count > self.hard_max_tokens:
                flush_current()
                for split_index, piece in enumerate(_split_text_recursive(unit.text, self.hard_max_tokens), start=1):
                    packed.append(
                        self._make_chunk(
                            document,
                            [
                                _UnitGroup(
                                    hierarchy_path=unit.hierarchy_path,
                                    unit_type=unit.unit_type,
                                    title=f"{unit.title} ({split_index})" if split_index > 1 else unit.title,
                                    text=piece,
                                    source_indexes=unit.source_indexes,
                                    order=unit.order,
                                )
                            ],
                            len(packed) + 1,
                        )
                    )
                continue

            combined_tokens = current_tokens + unit.token_count
            if current_units and current_tokens >= self.target_min_tokens and combined_tokens > self.target_max_tokens:
                flush_current()

            current_units.append(unit)
            current_tokens += unit.token_count

            if current_tokens >= self.target_max_tokens:
                flush_current()

        flush_current()
        return packed

    def _make_chunk(
        self,
        document: ExtractedDocument,
        units: list[_UnitGroup],
        chunk_order: int,
    ) -> dict[str, object]:
        text = "\n\n".join(unit.text for unit in units if unit.text)
        title = units[0].title if units else document.title
        hierarchy_path = units[0].hierarchy_path if units else ["Document"]
        return {
            "chunk_id": self._build_id(document.source_path, f"chunk-{chunk_order}"),
            "title": title,
            "text": text,
            "token_count": _count_tokens(text),
            "order": chunk_order,
            "hierarchy_path": hierarchy_path,
            "unit_type": units[0].unit_type if units else "generic",
            "source_indexes": [index for unit in units for index in unit.source_indexes],
        }

    def _build_nodes(
        self,
        document: ExtractedDocument,
        units: list[_UnitGroup],
        chunks: list[dict[str, object]],
        profile: str,
    ) -> list[HierarchyNode]:
        nodes: list[HierarchyNode] = []

        document_id = self._build_id(document.source_path, "document")
        nodes.append(
            HierarchyNode(
                node_id=document_id,
                label="Document",
                title=document.title,
                text="",
                parent_id=None,
                order=0,
                token_count=sum(_count_tokens(block.text) for block in document.blocks),
                metadata={
                    "source_path": str(document.source_path),
                    "file_type": document.file_type,
                    "profile": profile,
                    "document_metadata": document.metadata,
                },
            )
        )

        section_cache: dict[tuple[str, ...], str] = {}
        subsection_cache: dict[tuple[str, ...], str] = {}

        for unit in units:
            path = unit.hierarchy_path or ["Document"]
            section_title = path[0]
            section_path = (section_title,)
            if section_path not in section_cache:
                section_id = self._build_id(document.source_path, f"section::{section_title}")
                section_cache[section_path] = section_id
                nodes.append(
                    HierarchyNode(
                        node_id=section_id,
                        label="Section",
                        title=section_title,
                        parent_id=document_id,
                        order=len(section_cache),
                        token_count=0,
                        metadata={"hierarchy_path": list(section_path)},
                    )
                )

            if len(path) > 1:
                subsection_title = " / ".join(path[1:])
                subsection_path = (section_title, subsection_title)
                if subsection_path not in subsection_cache:
                    subsection_id = self._build_id(document.source_path, f"subsection::{section_title}::{subsection_title}")
                    subsection_cache[subsection_path] = subsection_id
                    nodes.append(
                        HierarchyNode(
                            node_id=subsection_id,
                            label="Subsection",
                            title=subsection_title,
                            parent_id=section_cache[section_path],
                            order=len(subsection_cache),
                            token_count=0,
                            metadata={"hierarchy_path": list(subsection_path)},
                        )
                    )

        for chunk in chunks:
            path = chunk["hierarchy_path"] if isinstance(chunk["hierarchy_path"], list) else ["Document"]
            section_title = path[0]
            parent_id = section_cache[(section_title,)]
            if len(path) > 1:
                subsection_title = " / ".join(path[1:])
                parent_id = subsection_cache[(section_title, subsection_title)]

            nodes.append(
                HierarchyNode(
                    node_id=str(chunk["chunk_id"]),
                    label="Chunk",
                    title=str(chunk["title"]),
                    text=str(chunk["text"]),
                    parent_id=parent_id,
                    order=int(chunk["order"]),
                    token_count=int(chunk["token_count"]),
                    metadata={
                        "unit_type": chunk["unit_type"],
                        "source_indexes": chunk["source_indexes"],
                        "token_count": chunk["token_count"],
                    },
                )
            )

        return nodes

    def _build_id(self, source_path: Path, suffix: str) -> str:
        digest = md5(f"{source_path.resolve()}::{suffix}".encode("utf-8"), usedforsecurity=False).hexdigest()
        return digest

