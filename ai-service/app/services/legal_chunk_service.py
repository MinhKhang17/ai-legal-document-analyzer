from __future__ import annotations

import re

from app.schemas import ChunkRecord, LegalUnit


class LegalChunkService:
    def __init__(self, max_chunk_size: int = 4000, overlap_size: int = 500) -> None:
        self.max_chunk_size = max_chunk_size
        self.overlap_size = overlap_size

    def chunk_legal_units(self, units: list[LegalUnit]) -> list[ChunkRecord]:
        chunks: list[ChunkRecord] = []
        buffer_units: list[LegalUnit] = []

        def flush_buffer() -> None:
            nonlocal buffer_units
            if not buffer_units:
                return
            merged = self._merge_units(buffer_units)
            chunks.extend(self._split_or_emit(merged))
            buffer_units = []

        for unit in units:
            if not buffer_units:
                buffer_units.append(unit)
                continue

            if self._can_merge(buffer_units[-1], unit, buffer_units):
                candidate = self._merge_units([*buffer_units, unit])
                if len(candidate["text"]) <= self.max_chunk_size:
                    buffer_units.append(unit)
                    continue

            flush_buffer()
            buffer_units.append(unit)

        flush_buffer()
        return [
            ChunkRecord(
                chunk_text=chunk["chunk_text"],
                chunk_index=index,
                page_number=chunk["page_number"],
                article_number=chunk["article_number"],
                clause_number=chunk["clause_number"],
                section_title=chunk["section_title"],
                unit_type=chunk["unit_type"],
            )
            for index, chunk in enumerate(chunks, start=1)
        ]

    def _can_merge(self, current: LegalUnit, next_unit: LegalUnit, buffer_units: list[LegalUnit]) -> bool:
        return (
            current.article_number == next_unit.article_number
            and current.clause_number == next_unit.clause_number
            and current.section_title == next_unit.section_title
            and next_unit.unit_type != "SECTION"
            and len(buffer_units) < 6
        )

    def _merge_units(self, units: list[LegalUnit]) -> dict[str, object]:
        text_parts = [unit.text.strip() for unit in units if unit.text.strip()]
        page_number = next((unit.page_number for unit in units if unit.page_number is not None), None)
        article_number = next((unit.article_number for unit in units if unit.article_number), None)
        clause_number = next((unit.clause_number for unit in units if unit.clause_number), None)
        section_title = next((unit.section_title for unit in units if unit.section_title), None)
        unit_type = self._choose_unit_type(units)
        return {
            "chunk_text": "\n\n".join(text_parts).strip(),
            "page_number": page_number,
            "article_number": article_number,
            "clause_number": clause_number,
            "section_title": section_title,
            "unit_type": unit_type,
        }

    def _choose_unit_type(self, units: list[LegalUnit]) -> str:
        priority = {"ARTICLE": 4, "CLAUSE": 3, "SECTION": 2, "PARAGRAPH": 1, "UNKNOWN": 0}
        return max(units, key=lambda unit: priority.get(unit.unit_type, 0)).unit_type

    def _split_or_emit(self, payload: dict[str, object]) -> list[dict[str, object]]:
        chunk_text = str(payload["chunk_text"])
        if len(chunk_text) <= self.max_chunk_size:
            return [payload]

        parts = self._split_long_text(chunk_text)
        if len(parts) <= 1:
            return [payload]

        chunks: list[dict[str, object]] = []
        for part in parts:
            new_payload = dict(payload)
            new_payload["chunk_text"] = part
            chunks.append(new_payload)
        return chunks

    def _split_long_text(self, text: str) -> list[str]:
        paragraphs = [part.strip() for part in re.split(r"\n{2,}", text) if part.strip()]
        if not paragraphs:
            paragraphs = [text.strip()]

        pieces: list[str] = []
        current = ""
        for paragraph in paragraphs:
            if not current:
                current = paragraph
                continue
            candidate = f"{current}\n\n{paragraph}".strip()
            if len(candidate) <= self.max_chunk_size:
                current = candidate
                continue
            pieces.extend(self._split_by_sentences(current))
            current = paragraph

        if current:
            pieces.extend(self._split_by_sentences(current))

        return [piece for piece in pieces if piece]

    def _split_by_sentences(self, text: str) -> list[str]:
        if len(text) <= self.max_chunk_size:
            return [text]

        sentences = [part.strip() for part in re.split(r"(?<=[.!?])\s+", text) if part.strip()]
        if len(sentences) <= 1:
            return self._split_by_characters(text)

        pieces: list[str] = []
        current = ""
        for sentence in sentences:
            if not current:
                current = sentence
                continue
            candidate = f"{current} {sentence}".strip()
            if len(candidate) <= self.max_chunk_size:
                current = candidate
            else:
                pieces.extend(self._split_by_characters(current))
                current = sentence
        if current:
            pieces.extend(self._split_by_characters(current))
        return [piece for piece in pieces if piece]

    def _split_by_characters(self, text: str) -> list[str]:
        pieces: list[str] = []
        if not text:
            return pieces

        start = 0
        while start < len(text):
            end = min(len(text), start + self.max_chunk_size)
            pieces.append(text[start:end].strip())
            if end >= len(text):
                break
            start = max(0, end - self.overlap_size)
        return [piece for piece in pieces if piece]
