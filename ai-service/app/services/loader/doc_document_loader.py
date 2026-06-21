from __future__ import annotations

import subprocess
from pathlib import Path

from app.models.knowledge_models import DocumentBlock, ExtractedDocument
from app.services.loader.base_loader import DocumentLoader
from app.services.loader.document_loaders import (
    group_lines_into_blocks,
    is_heading_like,
    looks_like_question,
    normalize_text,
)


class DocDocumentLoader(DocumentLoader):
    supported_extensions = (".doc",)

    def load(self, source_path: Path) -> ExtractedDocument:
        try:
            result = subprocess.run(
                ["antiword", str(source_path)],
                capture_output=True,
                text=True,
                check=True,
            )
        except FileNotFoundError as exc:
            raise RuntimeError(
                "antiword is required to load .doc files. Install the 'antiword' package in the runtime environment."
            ) from exc
        except subprocess.CalledProcessError as exc:
            stderr = (exc.stderr or "").strip()
            raise RuntimeError(
                f"Failed to extract text from .doc file: {stderr or exc}"
            ) from exc

        text = result.stdout or ""
        lines = text.splitlines()
        blocks = group_lines_into_blocks(lines)
        document_blocks: list[DocumentBlock] = []

        for block_index, block in enumerate(blocks, start=1):
            normalized = normalize_text(block)
            if not normalized:
                continue

            kind = "paragraph"
            heading_level = None
            if is_heading_like(normalized):
                kind = "heading"
                heading_level = 1
            elif looks_like_question(normalized):
                kind = "question"

            document_blocks.append(
                DocumentBlock(
                    text=normalized,
                    kind=kind,
                    order=block_index,
                    heading_level=heading_level,
                    metadata={"source": "doc"},
                )
            )

        return ExtractedDocument(
            source_path=source_path,
            title=source_path.stem,
            file_type="doc",
            blocks=document_blocks,
            metadata={
                "block_count": len(document_blocks),
                "extractor": "antiword",
            },
        )
