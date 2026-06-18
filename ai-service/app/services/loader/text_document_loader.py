from __future__ import annotations

from pathlib import Path

from app.models.knowledge_models import DocumentBlock, ExtractedDocument
from app.services.loader.base_loader import DocumentLoader
from app.services.loader.document_loaders import (
    group_lines_into_blocks,
    is_heading_like,
    looks_like_question,
    normalize_text,
)


class TextDocumentLoader(DocumentLoader):
    supported_extensions = (".txt",)

    def load(self, source_path: Path) -> ExtractedDocument:
        text = source_path.read_text(encoding="utf-8", errors="ignore")
        lines = text.splitlines()
        blocks = group_lines_into_blocks(lines)
        document_blocks: list[DocumentBlock] = []

        for block_index, block in enumerate(blocks, start=1):
            kind = "paragraph"
            heading_level = None
            if is_heading_like(block):
                kind = "heading"
                heading_level = 1
            elif looks_like_question(block):
                kind = "question"

            normalized = normalize_text(block)
            if not normalized:
                continue

            document_blocks.append(
                DocumentBlock(
                    text=normalized,
                    kind=kind,
                    order=block_index,
                    heading_level=heading_level,
                    metadata={"source": "txt"},
                )
            )

        return ExtractedDocument(
            source_path=source_path,
            title=source_path.stem,
            file_type="txt",
            blocks=document_blocks,
            metadata={
                "line_count": len(lines),
                "block_count": len(document_blocks),
            },
        )
