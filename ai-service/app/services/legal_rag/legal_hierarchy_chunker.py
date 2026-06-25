from __future__ import annotations

import re
from dataclasses import dataclass
from hashlib import md5
from pathlib import Path
from typing import Iterable

from app.models.knowledge_models import ChunkedDocument, DocumentBlock, ExtractedDocument, HierarchyNode


_META_LINE_PATTERNS = (
    re.compile(r"^công báo\s*/\s*số\b", re.IGNORECASE),
    re.compile(r"^c\b\w*\s*b\w*\b", re.IGNORECASE),
    re.compile(r"^loai tai lieu\b", re.IGNORECASE),
    re.compile(r"^vi tri\b", re.IGNORECASE),
    re.compile(r"^tieu de\b", re.IGNORECASE),
    re.compile(r"^noi dung\b", re.IGNORECASE),
    re.compile(r"^trang\s+\d+(\s*/\s*\d+)?$", re.IGNORECASE),
    re.compile(r"^page\s+\d+(\s*/\s*\d+)?$", re.IGNORECASE),
    re.compile(r"^phu luc\b", re.IGNORECASE),
    re.compile(r"^mau so\b", re.IGNORECASE),
    re.compile(r"^mẫu số\b", re.IGNORECASE),
)

_HEADER_FOOTER_PATTERNS = (
    re.compile(r"^cộng hòa xã hội chủ nghĩa việt nam\b", re.IGNORECASE),
    re.compile(r"^độc lập\s*-\s*tự do\s*-\s*hạnh phúc\b", re.IGNORECASE),
    re.compile(r"^công báo\s*/\s*số\b", re.IGNORECASE),
    re.compile(r"^số:\s*\d+", re.IGNORECASE),
)

_SPLIT_MARKER_PATTERN = re.compile(
    r"(Phần\s+thứ\s+[^\s,.;:]+|Chương\s+[IVXLC\d]+|Mục\s+\d+|Điều\s+\d+\.?|Khoản\s+\d+\.?|\d+\.\s+|[a-zđ]\)\s+)",
    re.IGNORECASE,
)

_SENTENCE_SPLIT_PATTERN = re.compile(r"(?<=[.!?…។])\s+")

_WORD_PATTERN = re.compile(r"\S+")


def _fold(text: str) -> str:
    import unicodedata

    normalized = unicodedata.normalize("NFKD", text)
    stripped = "".join(char for char in normalized if not unicodedata.combining(char))
    stripped = stripped.replace("Đ", "D").replace("đ", "d")
    return re.sub(r"\s+", " ", stripped).strip().lower()


def _token_count(text: str) -> int:
    return max(0, len(_WORD_PATTERN.findall(text)))


def _normalize_space(text: str) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    text = re.sub(r"\s+([,.;:!?])", r"\1", text)
    text = re.sub(r"([(\[])\s+", r"\1", text)
    text = re.sub(r"\s+([)\]])", r"\1", text)
    return text


def _is_metadata_line(text: str) -> bool:
    folded = _fold(text)
    return any(pattern.match(folded) for pattern in _META_LINE_PATTERNS)


def _is_header_footer(text: str) -> bool:
    folded = _fold(text)
    return any(pattern.match(folded) for pattern in _HEADER_FOOTER_PATTERNS)


def _split_merged_markers(text: str) -> list[str]:
    matches = list(_SPLIT_MARKER_PATTERN.finditer(text))
    if len(matches) <= 1:
        return [text]

    parts: list[str] = []
    if matches[0].start() > 0:
        prefix = text[: matches[0].start()].strip()
        if prefix:
            parts.append(prefix)

    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        piece = text[start:end].strip()
        if piece:
            parts.append(piece)

    return parts


def _split_lines(text: str) -> list[str]:
    raw_lines = [line.strip() for line in text.splitlines()]
    lines: list[str] = []
    for raw_line in raw_lines:
        if not raw_line:
            continue
        if _is_metadata_line(raw_line) or _is_header_footer(raw_line):
            continue
        for segment in _split_merged_markers(raw_line):
            segment = _normalize_space(segment)
            if segment and not _is_metadata_line(segment) and not _is_header_footer(segment):
                lines.append(segment)
    return lines


def _build_sentence_groups(text: str, target_tokens: int, max_tokens: int, overlap_tokens: int) -> list[str]:
    cleaned = _normalize_space(text)
    if not cleaned:
        return []
    if _token_count(cleaned) <= max_tokens:
        return [cleaned]

    sentences = [piece.strip() for piece in _SENTENCE_SPLIT_PATTERN.split(cleaned) if piece.strip()]
    
    # Pre-split sentences that are too large to prevent infinite loops in grouping/flushing
    split_sentences = []
    for s in sentences:
        if _token_count(s) <= max_tokens:
            split_sentences.append(s)
        else:
            words = s.split()
            start = 0
            while start < len(words):
                split_sentences.append(" ".join(words[start : start + target_tokens]).strip())
                start += target_tokens
    sentences = split_sentences

    if len(sentences) <= 1:
        return sentences

    groups: list[str] = []
    current_sentences: list[str] = []
    current_tokens = 0
    overlap_buffer: list[str] = []

    def flush(with_overlap: bool) -> None:
        nonlocal current_sentences, current_tokens, overlap_buffer
        if not current_sentences:
            return
        groups.append(_normalize_space(" ".join(current_sentences)))
        if with_overlap and overlap_tokens > 0:
            overlap_buffer = []
            overlap_count = 0
            for sentence in reversed(current_sentences):
                sentence_tokens = _token_count(sentence)
                overlap_buffer.insert(0, sentence)
                overlap_count += sentence_tokens
                if overlap_count >= overlap_tokens:
                    break
        else:
            overlap_buffer = []
        current_sentences = overlap_buffer[:]
        current_tokens = _token_count(" ".join(current_sentences))

    for sentence in sentences:
        sentence_tokens = _token_count(sentence)
        if current_sentences and current_tokens + sentence_tokens > max_tokens:
            flush(with_overlap=True)
            # If after flushing, combining with the overlap still exceeds max_tokens, drop the overlap
            if current_tokens + sentence_tokens > max_tokens:
                current_sentences = []
                current_tokens = 0
        current_sentences.append(sentence)
        current_tokens += sentence_tokens
        if current_tokens >= target_tokens and current_tokens <= max_tokens:
            flush(with_overlap=True)

    if current_sentences:
        flush(with_overlap=False)

    return [group for group in groups if group]


def _clean_body_text(lines: Iterable[str]) -> str:
    return _normalize_space(" ".join(line.strip() for line in lines if line and line.strip()))


@dataclass(frozen=True)
class _HierarchyState:
    part_title: str | None = None
    chapter_title: str | None = None
    section_title: str | None = None
    article_title: str | None = None
    clause_no: str | None = None
    clause_title: str | None = None
    point_no: str | None = None
    point_title: str | None = None

    def parent_label(self) -> str:
        if self.point_title:
            return "Point"
        if self.clause_title:
            return "Clause"
        return "Article"


@dataclass(frozen=True)
class _ChunkPlan:
    title: str
    text: str
    parent_id: str
    parent_label: str
    order_index: int
    legal_path: str
    chunk_type: str
    metadata: dict[str, object]


class _BaseLegalHierarchyChunker:
    chunk_version = 1
    target_tokens = 300
    max_tokens = 500
    overlap_tokens = 50

    def chunk_document(self, document: ExtractedDocument) -> ChunkedDocument:
        document_id = self._build_id(document.source_path, f"document::{document.title}")
        doc_title = _normalize_space(document.title or document.source_path.stem or "Document")
        lines = _split_lines("\n".join(block.text for block in document.blocks))
        if not lines:
            lines = [_normalize_space(block.text) for block in document.blocks if _normalize_space(block.text)]

        nodes: list[HierarchyNode] = [
            HierarchyNode(
                node_id=document_id,
                label="Document",
                title=doc_title,
                text=doc_title,
                parent_id=None,
                order=0,
                token_count=_token_count(doc_title),
                metadata={
                    "document_id": document_id,
                    "doc_title": doc_title,
                    "source_file": str(document.source_path.name),
                    "legal_type": document.file_type,
                    "chunk_version": self.chunk_version,
                    "chunking_strategy": self.__class__.__name__,
                },
            )
        ]

        parent_nodes: dict[tuple[str, str], str] = {}
        chunks: list[_ChunkPlan] = []
        warnings: list[str] = []
        skipped_chunks = 0

        state = _HierarchyState()
        body_lines: list[str] = []
        seen_signatures: set[str] = set()

        def current_parent_id() -> str:
            next_order = lambda: len(nodes) + 1
            if state.point_title:
                return self._ensure_point_node(nodes, parent_nodes, document_id, doc_title, state, next_order)
            if state.clause_title:
                return self._ensure_clause_node(nodes, parent_nodes, document_id, doc_title, state, next_order)
            if state.article_title:
                return self._ensure_article_node(nodes, parent_nodes, document_id, doc_title, state, next_order)
            if state.section_title:
                return self._ensure_section_node(nodes, parent_nodes, document_id, doc_title, state, next_order)
            if state.chapter_title:
                return self._ensure_chapter_node(nodes, parent_nodes, document_id, doc_title, state, next_order)
            if state.part_title:
                return self._ensure_part_node(nodes, parent_nodes, document_id, doc_title, state, next_order)
            return document_id

        def flush_leaf() -> None:
            nonlocal body_lines, skipped_chunks
            if not body_lines:
                return
            parent_id = current_parent_id()
            leaf_text = _clean_body_text(body_lines)
            body_lines = []
            if not leaf_text:
                return

            leaf_type = state.parent_label() if state.article_title else "document"
            legal_path = self._build_legal_path(doc_title, state)
            chunks_to_emit = list(self._split_leaf_text(leaf_text))
            split_index = 0
            while split_index < len(chunks_to_emit):
                chunk_text = chunks_to_emit[split_index]
                split_index += 1
                content = _normalize_space(chunk_text)
                if not content:
                    skipped_chunks += 1
                    continue
                token_count = _token_count(content)
                if token_count > self.max_tokens:
                    parts = _build_sentence_groups(content, self.target_tokens, self.max_tokens, self.overlap_tokens)
                    if len(parts) == 1 and _token_count(parts[0]) > self.max_tokens:
                        skipped_chunks += 1
                        continue
                    chunks_to_emit[split_index:split_index] = parts
                    continue

                if token_count > 400:
                    warnings.append(f"Chunk above 400 tokens at {legal_path} ({token_count} tokens)")

                embedding_text = f"{legal_path} > {content}" if legal_path else content
                signature = md5(f"{document_id}::{legal_path}::{split_index}::{_normalize_space(content)}".encode("utf-8"), usedforsecurity=False).hexdigest()
                if signature in seen_signatures:
                    skipped_chunks += 1
                    continue
                seen_signatures.add(signature)

                chunk_id = self._build_id(document.source_path, f"{document_id}::{legal_path}::{split_index}")
                title = self._chunk_title(state, doc_title)
                order_index = len(chunks) + 1
                chunk_plan = _ChunkPlan(
                    title=title,
                    text=embedding_text,
                    parent_id=parent_id,
                    parent_label=state.parent_label() if state.article_title else "Document",
                    order_index=order_index,
                    legal_path=legal_path,
                    chunk_type=self._chunk_type(state, content),
                    metadata={
                        "document_id": document_id,
                        "doc_title": doc_title,
                        "source_file": str(document.source_path.name),
                        "legal_type": document.file_type,
                        "part_title": state.part_title,
                        "chapter_title": state.chapter_title,
                        "section_title": state.section_title,
                        "article_no": self._article_no(state.article_title),
                        "article_title": state.article_title,
                        "clause_no": state.clause_no,
                        "point_no": state.point_no,
                        "chunk_type": self._chunk_type(state, content),
                        "chunk_level": self._chunk_type(state, content),
                        "parent_id": parent_id,
                        "order_index": order_index,
                        "token_count": token_count,
                        "text": embedding_text,
                        "legal_path": legal_path,
                        "parent_path": legal_path,
                        "chunk_version": self.chunk_version,
                        "chunking_strategy": self.__class__.__name__,
                        "embedding_text": embedding_text,
                        "retrieval_text": embedding_text,
                        "chunk_id": chunk_id,
                        "hierarchy_path": [
                            value
                            for value in [
                                doc_title,
                                state.part_title,
                                state.chapter_title,
                                state.section_title,
                                state.article_title,
                                state.clause_title,
                                state.point_title,
                            ]
                            if value
                        ],
                    },
                )
                chunks.append(chunk_plan)

                nodes.append(
                    HierarchyNode(
                        node_id=chunk_id,
                        label="Chunk",
                        title=title,
                        text=embedding_text,
                        parent_id=parent_id,
                        order=order_index,
                        token_count=token_count,
                        metadata={
                            **chunk_plan.metadata,
                        },
                        embedding=self._embed_text(embedding_text),
                    )
                )

        for raw_line in lines:
            marker = self._detect_marker(raw_line)
            if marker is not None:
                flush_leaf()
                state = self._update_state(state, marker)
                self._ensure_node_for_marker(nodes, parent_nodes, document_id, doc_title, state, marker)
                marker_type, marker_text = marker
                if marker_type in {"article", "clause", "point"}:
                    body_lines = [_normalize_space(marker_text)]
                continue

            body_lines.append(raw_line)

        flush_leaf()

        chunk_nodes = [node for node in nodes if node.label == "Chunk"]
        total_tokens = [node.token_count for node in chunk_nodes]
        avg_tokens = round(sum(total_tokens) / len(total_tokens), 2) if total_tokens else 0.0
        max_tokens = max(total_tokens) if total_tokens else 0

        metadata = {
            "document_id": document_id,
            "doc_title": doc_title,
            "source_file": str(document.source_path.name),
            "legal_type": document.file_type,
            "chunk_version": self.chunk_version,
            "chunking_strategy": self.__class__.__name__,
            "total_parent_nodes": sum(1 for node in nodes if node.label in {"Part", "Chapter", "Section", "Article", "Clause", "Point"}),
            "total_child_chunks": len(chunk_nodes),
            "embedded_chunks": len(chunk_nodes),
            "skipped_chunks": skipped_chunks,
            "avg_chunk_tokens": avg_tokens,
            "max_chunk_tokens": max_tokens,
            "warnings": warnings,
            "source_metadata": document.metadata,
        }

        return ChunkedDocument(
            document_id=document_id,
            title=doc_title,
            source_path=document.source_path,
            file_type=document.file_type,
            nodes=nodes,
            metadata=metadata,
        )

    def _detect_marker(self, text: str) -> tuple[str, str] | None:
        folded = _fold(text)
        patterns = (
            ("part", r"^phan thu\s+(.+)$"),
            ("chapter", r"^chuong\s+([ivxlcd\d]+)(?:\s+.*)?$"),
            ("section", r"^muc\s+(\d+)(?:\s+.*)?$"),
            ("article", r"^dieu\s+(\d+)\.?\s*(.*)$"),
            ("clause", r"^(?:khoan\s+)?(\d+)\.?\s*(.*)$"),
            ("point", r"^([a-zd])\)\s*(.*)$"),
        )
        for marker_type, pattern in patterns:
            match = re.match(pattern, folded, flags=re.IGNORECASE)
            if match:
                return marker_type, text
        return None

    def _update_state(self, state: _HierarchyState, marker: tuple[str, str]) -> _HierarchyState:
        marker_type, raw_text = marker
        cleaned = _normalize_space(raw_text)
        if marker_type == "part":
            return _HierarchyState(part_title=cleaned)
        if marker_type == "chapter":
            return _HierarchyState(part_title=state.part_title, chapter_title=cleaned)
        if marker_type == "section":
            return _HierarchyState(part_title=state.part_title, chapter_title=state.chapter_title, section_title=cleaned)
        if marker_type == "article":
            return _HierarchyState(
                part_title=state.part_title,
                chapter_title=state.chapter_title,
                section_title=state.section_title,
                article_title=cleaned,
            )
        if marker_type == "clause":
            return _HierarchyState(
                part_title=state.part_title,
                chapter_title=state.chapter_title,
                section_title=state.section_title,
                article_title=state.article_title,
                clause_no=self._extract_clause_no(cleaned),
                clause_title=cleaned,
            )
        if marker_type == "point":
            return _HierarchyState(
                part_title=state.part_title,
                chapter_title=state.chapter_title,
                section_title=state.section_title,
                article_title=state.article_title,
                clause_no=state.clause_no,
                clause_title=state.clause_title,
                point_no=self._extract_point_no(cleaned),
                point_title=cleaned,
            )
        return state

    def _ensure_node_for_marker(
        self,
        nodes: list[HierarchyNode],
        parent_nodes: dict[tuple[str, str], str],
        document_id: str,
        doc_title: str,
        state: _HierarchyState,
        marker: tuple[str, str],
    ) -> None:
        marker_type, _ = marker
        if marker_type == "part" and state.part_title:
            self._ensure_part_node(nodes, parent_nodes, document_id, doc_title, state, lambda: len(nodes))
        elif marker_type == "chapter" and state.chapter_title:
            self._ensure_chapter_node(nodes, parent_nodes, document_id, doc_title, state, lambda: len(nodes))
        elif marker_type == "section" and state.section_title:
            self._ensure_section_node(nodes, parent_nodes, document_id, doc_title, state, lambda: len(nodes))
        elif marker_type == "article" and state.article_title:
            self._ensure_article_node(nodes, parent_nodes, document_id, doc_title, state, lambda: len(nodes))
        elif marker_type == "clause" and state.clause_title:
            self._ensure_clause_node(nodes, parent_nodes, document_id, doc_title, state, lambda: len(nodes))
        elif marker_type == "point" and state.point_title:
            self._ensure_point_node(nodes, parent_nodes, document_id, doc_title, state, lambda: len(nodes))

    def _ensure_part_node(self, nodes, parent_nodes, document_id, doc_title, state, order_fn) -> str:
        key = ("Part", state.part_title or "")
        if key in parent_nodes:
            return parent_nodes[key]
        parent_id = document_id
        node_id = self._build_id(document_id, f"part::{state.part_title}")
        parent_nodes[key] = node_id
        nodes.append(
            HierarchyNode(
                node_id=node_id,
                label="Part",
                title=state.part_title or "",
                text=state.part_title or "",
                parent_id=parent_id,
                order=order_fn(),
                token_count=_token_count(state.part_title or ""),
                metadata={"legal_type": "part", "doc_title": doc_title, "part_title": state.part_title or ""},
            )
        )
        return node_id

    def _ensure_chapter_node(self, nodes, parent_nodes, document_id, doc_title, state, order_fn) -> str:
        parent_id = self._ensure_part_node(nodes, parent_nodes, document_id, doc_title, state, order_fn) if state.part_title else document_id
        key = ("Chapter", f"{state.part_title or ''}::{state.chapter_title or ''}")
        if key in parent_nodes:
            return parent_nodes[key]
        node_id = self._build_id(document_id, f"chapter::{state.part_title}::{state.chapter_title}")
        parent_nodes[key] = node_id
        nodes.append(
            HierarchyNode(
                node_id=node_id,
                label="Chapter",
                title=state.chapter_title or "",
                text=state.chapter_title or "",
                parent_id=parent_id,
                order=order_fn(),
                token_count=_token_count(state.chapter_title or ""),
                metadata={"legal_type": "chapter", "doc_title": doc_title, "chapter_title": state.chapter_title or ""},
            )
        )
        return node_id

    def _ensure_section_node(self, nodes, parent_nodes, document_id, doc_title, state, order_fn) -> str:
        parent_id = document_id
        if state.chapter_title:
            parent_id = self._ensure_chapter_node(nodes, parent_nodes, document_id, doc_title, state, order_fn)
        elif state.part_title:
            parent_id = self._ensure_part_node(nodes, parent_nodes, document_id, doc_title, state, order_fn)
        key = ("Section", f"{state.part_title or ''}::{state.chapter_title or ''}::{state.section_title or ''}")
        if key in parent_nodes:
            return parent_nodes[key]
        node_id = self._build_id(document_id, f"section::{state.part_title}::{state.chapter_title}::{state.section_title}")
        parent_nodes[key] = node_id
        nodes.append(
            HierarchyNode(
                node_id=node_id,
                label="Section",
                title=state.section_title or "",
                text=state.section_title or "",
                parent_id=parent_id,
                order=order_fn(),
                token_count=_token_count(state.section_title or ""),
                metadata={"legal_type": "section", "doc_title": doc_title, "section_title": state.section_title or ""},
            )
        )
        return node_id

    def _ensure_article_node(self, nodes, parent_nodes, document_id, doc_title, state, order_fn) -> str:
        parent_id = document_id
        if state.section_title:
            parent_id = self._ensure_section_node(nodes, parent_nodes, document_id, doc_title, state, order_fn)
        elif state.chapter_title:
            parent_id = self._ensure_chapter_node(nodes, parent_nodes, document_id, doc_title, state, order_fn)
        elif state.part_title:
            parent_id = self._ensure_part_node(nodes, parent_nodes, document_id, doc_title, state, order_fn)
        key = ("Article", f"{state.part_title or ''}::{state.chapter_title or ''}::{state.section_title or ''}::{state.article_title or ''}")
        if key in parent_nodes:
            return parent_nodes[key]
        node_id = self._build_id(document_id, f"article::{state.part_title}::{state.chapter_title}::{state.section_title}::{state.article_title}")
        parent_nodes[key] = node_id
        nodes.append(
            HierarchyNode(
                node_id=node_id,
                label="Article",
                title=state.article_title or "",
                text=state.article_title or "",
                parent_id=parent_id,
                order=order_fn(),
                token_count=_token_count(state.article_title or ""),
                metadata={
                    "legal_type": "article",
                    "doc_title": doc_title,
                    "article_no": self._article_no(state.article_title),
                    "article_title": state.article_title or "",
                },
            )
        )
        return node_id

    def _ensure_clause_node(self, nodes, parent_nodes, document_id, doc_title, state, order_fn) -> str:
        parent_id = self._ensure_article_node(nodes, parent_nodes, document_id, doc_title, state, order_fn)
        key = ("Clause", f"{state.article_title or ''}::{state.clause_title or ''}")
        if key in parent_nodes:
            return parent_nodes[key]
        node_id = self._build_id(document_id, f"clause::{state.article_title}::{state.clause_title}")
        parent_nodes[key] = node_id
        nodes.append(
            HierarchyNode(
                node_id=node_id,
                label="Clause",
                title=state.clause_title or "",
                text=state.clause_title or "",
                parent_id=parent_id,
                order=order_fn(),
                token_count=_token_count(state.clause_title or ""),
                metadata={
                    "legal_type": "clause",
                    "doc_title": doc_title,
                    "clause_no": state.clause_no,
                    "clause_title": state.clause_title or "",
                },
            )
        )
        return node_id

    def _ensure_point_node(self, nodes, parent_nodes, document_id, doc_title, state, order_fn) -> str:
        parent_id = self._ensure_clause_node(nodes, parent_nodes, document_id, doc_title, state, order_fn)
        key = ("Point", f"{state.clause_title or ''}::{state.point_title or ''}")
        if key in parent_nodes:
            return parent_nodes[key]
        node_id = self._build_id(document_id, f"point::{state.clause_title}::{state.point_title}")
        parent_nodes[key] = node_id
        nodes.append(
            HierarchyNode(
                node_id=node_id,
                label="Point",
                title=state.point_title or "",
                text=state.point_title or "",
                parent_id=parent_id,
                order=order_fn(),
                token_count=_token_count(state.point_title or ""),
                metadata={
                    "legal_type": "point",
                    "doc_title": doc_title,
                    "point_no": state.point_no,
                    "point_title": state.point_title or "",
                },
            )
        )
        return node_id

    def _split_leaf_text(self, text: str) -> list[str]:
        cleaned = _normalize_space(text)
        if not cleaned:
            return []
        if _token_count(cleaned) <= self.max_tokens:
            return [cleaned]
        return _build_sentence_groups(cleaned, self.target_tokens, self.max_tokens, self.overlap_tokens)

    def _build_legal_path(self, doc_title: str, state: _HierarchyState) -> str:
        parts = [doc_title]
        for value in [state.part_title, state.chapter_title, state.section_title, state.article_title, state.clause_title, state.point_title]:
            if value and value not in parts:
                parts.append(value)
        return " > ".join(parts)

    def _chunk_title(self, state: _HierarchyState, doc_title: str) -> str:
        for value in [state.point_title, state.clause_title, state.article_title, state.section_title, state.chapter_title, state.part_title]:
            if value:
                return value
        return doc_title

    def _chunk_type(self, state: _HierarchyState, content: str) -> str:
        if state.point_title:
            return "point"
        if state.clause_title:
            return "clause"
        if state.article_title:
            return "article"
        if state.section_title:
            return "section"
        if state.chapter_title:
            return "chapter"
        if state.part_title:
            return "part"
        return "sentence_group" if _token_count(content) > self.target_tokens else "chunk"

    def _article_no(self, title: str | None) -> str | None:
        if not title:
            return None
        match = re.search(r"(\d+)", _fold(title))
        return match.group(1) if match else None

    def _extract_clause_no(self, title: str) -> str | None:
        match = re.match(r"^(?:khoan\s+)?(\d+)", _fold(title))
        return match.group(1) if match else None

    def _extract_point_no(self, title: str) -> str | None:
        match = re.match(r"^([a-zd])\)", _fold(title))
        return match.group(1) if match else None

    def _embed_text(self, text: str) -> list[float] | None:
        from app.services.document_embedding_service import HashingEmbeddingProvider

        return HashingEmbeddingProvider().embed([text])[0]

    def _build_id(self, source_path: Path | str, suffix: str) -> str:
        if isinstance(source_path, Path):
            base = str(source_path.resolve())
        else:
            base = str(source_path)
        return md5(f"{base}::{suffix}".encode("utf-8"), usedforsecurity=False).hexdigest()


class LegalHierarchyChunker(_BaseLegalHierarchyChunker):
    pass


class LegalHierarchyChunkerV2(_BaseLegalHierarchyChunker):
    chunk_version = 2
    target_tokens = 260
    max_tokens = 320
    overlap_tokens = 50
