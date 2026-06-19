from __future__ import annotations

from pathlib import Path

from app.models.knowledge_models import DocumentBlock, ExtractedDocument
from app.services.loader.base_loader import DocumentLoader
from app.services.loader.document_loaders import (
    is_heading_like,
    looks_like_question,
    normalize_text,
)


class WordDocumentLoader(DocumentLoader):
    supported_extensions = (".docx", ".doc")

    def load(self, source_path: Path) -> ExtractedDocument:
        # For .doc files, try to convert to .docx first or use alternative loader
        if source_path.suffix.lower() == ".doc":
            return self._load_doc_file(source_path)
        
        try:
            from docx import Document
        except ImportError as exc:
            raise RuntimeError(
                "python-docx is required to load .docx files. "
                "Install the 'python-docx' package in the runtime environment."
            ) from exc

        document = Document(str(source_path))
        blocks: list[DocumentBlock] = []

        def append_block(text: str, kind: str = "paragraph", heading_level: int | None = None) -> None:
            normalized = normalize_text(text)
            if not normalized:
                return

            blocks.append(
                DocumentBlock(
                    text=normalized,
                    kind=kind,
                    order=len(blocks) + 1,
                    heading_level=heading_level,
                    metadata={"source": "docx"},
                )
            )

        for paragraph in document.paragraphs:
            text = paragraph.text or ""
            if not text.strip():
                continue

            style_name = getattr(paragraph.style, "name", "") or ""
            kind = "paragraph"
            heading_level = None
            if is_heading_like(text) or style_name.lower().startswith("heading"):
                kind = "heading"
                heading_level = 1
            elif looks_like_question(text):
                kind = "question"

            append_block(text, kind=kind, heading_level=heading_level)

        for table_index, table in enumerate(document.tables, start=1):
            for row_index, row in enumerate(table.rows, start=1):
                for cell_index, cell in enumerate(row.cells, start=1):
                    cell_text = normalize_text(cell.text or "")
                    if not cell_text:
                        continue
                    append_block(
                        f"[Table {table_index} R{row_index}C{cell_index}] {cell_text}",
                        kind="table_cell",
                    )

        return ExtractedDocument(
            source_path=source_path,
            title=source_path.stem,
            file_type="docx",
            blocks=blocks,
            metadata={
                "paragraph_count": len(document.paragraphs),
                "table_count": len(document.tables),
                "block_count": len(blocks),
            },
        )
    
    def _load_doc_file(self, source_path: Path) -> ExtractedDocument:
        """Load legacy .doc files using antiword or text extraction fallback."""
        import subprocess
        import tempfile
        
        # Try antiword first (if available)
        try:
            result = subprocess.run(
                ["antiword", str(source_path)],
                capture_output=True,
                text=True,
                timeout=30,
                check=False
            )
            if result.returncode == 0 and result.stdout:
                text = result.stdout
                return self._parse_text_to_document(source_path, text)
        except (FileNotFoundError, subprocess.TimeoutExpired):
            pass
        
        # Try textract (if available)
        try:
            import textract
            text = textract.process(str(source_path)).decode('utf-8', errors='ignore')
            if text.strip():
                return self._parse_text_to_document(source_path, text)
        except (ImportError, Exception):
            pass
        
        # Fallback: Try converting to docx using LibreOffice (if available)
        try:
            with tempfile.TemporaryDirectory() as tmp_dir:
                result = subprocess.run(
                    ["soffice", "--headless", "--convert-to", "docx", "--outdir", tmp_dir, str(source_path)],
                    capture_output=True,
                    timeout=60,
                    check=False
                )
                if result.returncode == 0:
                    docx_path = Path(tmp_dir) / f"{source_path.stem}.docx"
                    if docx_path.exists():
                        from docx import Document
                        document = Document(str(docx_path))
                        # Use the same parsing logic as .docx files
                        blocks: list[DocumentBlock] = []
                        
                        for paragraph in document.paragraphs:
                            text = paragraph.text or ""
                            if not text.strip():
                                continue
                            
                            normalized = normalize_text(text)
                            if not normalized:
                                continue
                            
                            style_name = getattr(paragraph.style, "name", "") or ""
                            kind = "paragraph"
                            heading_level = None
                            if is_heading_like(text) or style_name.lower().startswith("heading"):
                                kind = "heading"
                                heading_level = 1
                            elif looks_like_question(text):
                                kind = "question"
                            
                            blocks.append(
                                DocumentBlock(
                                    text=normalized,
                                    kind=kind,
                                    order=len(blocks) + 1,
                                    heading_level=heading_level,
                                    metadata={"source": "doc_converted"},
                                )
                            )
                        
                        return ExtractedDocument(
                            source_path=source_path,
                            title=source_path.stem,
                            file_type="doc",
                            blocks=blocks,
                            metadata={
                                "block_count": len(blocks),
                                "conversion_method": "libreoffice",
                            },
                        )
        except (FileNotFoundError, subprocess.TimeoutExpired, Exception):
            pass
        
        # Last resort: Return error block
        return ExtractedDocument(
            source_path=source_path,
            title=source_path.stem,
            file_type="doc",
            blocks=[
                DocumentBlock(
                    text=f"[ERROR] Cannot process legacy .doc file: {source_path.name}. Please convert to .docx format.",
                    kind="error",
                    order=1,
                    metadata={"source": "error"},
                )
            ],
            metadata={"error": "legacy_doc_format_not_supported"},
        )
    
    def _parse_text_to_document(self, source_path: Path, text: str) -> ExtractedDocument:
        """Parse extracted text into document blocks."""
        blocks: list[DocumentBlock] = []
        
        # Split by lines and group into blocks
        lines = text.split('\n')
        current_block = []
        
        for line in lines:
            line = line.strip()
            if not line:
                if current_block:
                    full_text = ' '.join(current_block)
                    normalized = normalize_text(full_text)
                    if normalized:
                        kind = "heading" if is_heading_like(normalized) else "paragraph"
                        blocks.append(
                            DocumentBlock(
                                text=normalized,
                                kind=kind,
                                order=len(blocks) + 1,
                                metadata={"source": "doc_text_extraction"},
                            )
                        )
                    current_block = []
                continue
            current_block.append(line)
        
        # Add remaining block
        if current_block:
            full_text = ' '.join(current_block)
            normalized = normalize_text(full_text)
            if normalized:
                kind = "heading" if is_heading_like(normalized) else "paragraph"
                blocks.append(
                    DocumentBlock(
                        text=normalized,
                        kind=kind,
                        order=len(blocks) + 1,
                        metadata={"source": "doc_text_extraction"},
                    )
                )
        
        return ExtractedDocument(
            source_path=source_path,
            title=source_path.stem,
            file_type="doc",
            blocks=blocks,
            metadata={
                "block_count": len(blocks),
                "extraction_method": "text",
            },
        )
