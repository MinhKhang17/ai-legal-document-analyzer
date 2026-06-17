from __future__ import annotations

from pathlib import Path
from typing import Any

from pdf2image import convert_from_path
from pypdf import PdfReader

from app.models.knowledge_models import DocumentBlock, ExtractedDocument
from app.services.loader.base_loader import DocumentLoader
from app.services.loader.document_loaders import (
    group_lines_into_blocks,
    is_heading_like,
    looks_like_question,
)
from app.services.ocr_service import OCRService


class PdfDocumentLoader(DocumentLoader):
    supported_extensions = (".pdf",)

    def __init__(self, ocr_service: OCRService | None = None) -> None:
        self._ocr_service = ocr_service

    def _get_ocr_service(self) -> OCRService:
        if self._ocr_service is None:
            self._ocr_service = OCRService()
        return self._ocr_service

    def _classify_pdf(self, reader: PdfReader) -> str:
        total_pages = len(reader.pages)
        if total_pages == 0:
            return "unknown"

        text_pages = 0
        for page in reader.pages:
            text = page.extract_text() or ""
            if len(text.strip()) >= 50:
                text_pages += 1

        ratio = text_pages / total_pages
        if ratio >= 0.8:
            return "text"
        if ratio <= 0.2:
            return "scan"
        return "hybrid"

    def _ocr_image(self, image: Any) -> str:
        return self._get_ocr_service().extract_text_from_pil_image(image)

    def _extract_page_images(self, page: Any) -> list[Any]:
        images: list[Any] = []
        page_images = getattr(page, "images", None)
        if not page_images:
            return images

        for image_file in page_images:
            image = getattr(image_file, "image", None)
            if image is not None:
                images.append(image)
                continue

            data = getattr(image_file, "data", None)
            if not data:
                continue

            try:
                from io import BytesIO
                from PIL import Image

                images.append(Image.open(BytesIO(data)))
            except Exception:
                continue

        return images

    def _render_page_image(self, source_path: Path, page_number: int) -> Any | None:
        try:
            rendered = convert_from_path(
                str(source_path),
                dpi=300,
                first_page=page_number,
                last_page=page_number,
            )
        except Exception:
            return None

        if not rendered:
            return None
        return rendered[0]

    def _extract_page_text(self, source_path: Path, page_number: int, page: Any) -> tuple[str, str]:
        text = page.extract_text() or ""
        if text.strip():
            return text, "pdf_text"

        page_images = self._extract_page_images(page)
        if page_images:
            page_images.sort(
                key=lambda image: getattr(image, "width", 0) * getattr(image, "height", 0),
                reverse=True,
            )
            for page_image in page_images:
                try:
                    ocr_text = self._ocr_image(page_image)
                except Exception:
                    continue
                if ocr_text.strip():
                    return ocr_text, "ocr_image"

        page_image = self._render_page_image(source_path, page_number)
        if page_image is None:
            return "", "empty"

        try:
            ocr_text = self._ocr_image(page_image)
            if ocr_text.strip():
                return ocr_text, "ocr"
        except Exception:
            pass

        return "", "empty"

    def load(self, source_path: Path) -> ExtractedDocument:
        reader = PdfReader(str(source_path))
        pdf_type = self._classify_pdf(reader)
        blocks: list[DocumentBlock] = []

        for page_index, page in enumerate(reader.pages, start=1):
            page_text, source = self._extract_page_text(
                source_path=source_path,
                page_number=page_index,
                page=page,
            )
            page_blocks = group_lines_into_blocks(page_text.splitlines())

            for block_index, block in enumerate(page_blocks, start=1):
                kind = "paragraph"
                heading_level = None
                if is_heading_like(block):
                    kind = "heading"
                    heading_level = 1
                elif looks_like_question(block):
                    kind = "question"

                blocks.append(
                    DocumentBlock(
                        text=block,
                        kind=kind,
                        order=len(blocks) + 1,
                        page_number=page_index,
                        heading_level=heading_level,
                        metadata={
                            "page_block_index": block_index,
                            "source": source,
                            "pdf_type": pdf_type,
                        },
                    )
                )

        return ExtractedDocument(
            source_path=source_path,
            title=source_path.stem,
            file_type="pdf",
            blocks=blocks,
            metadata={
                "page_count": len(reader.pages),
                "pdf_type": pdf_type,
                "block_count": len(blocks),
            },
        )
