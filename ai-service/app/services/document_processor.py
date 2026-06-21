from __future__ import annotations

import logging
from pathlib import Path

from app.models.knowledge_models import ExtractedDocument
from app.schemas import ChunkRecord, DocumentProcessRequest, DocumentProcessResult, ExtractedPage, PageDebugInfo
from app.services.callback_client import CallbackClient
from app.services.extraction_cache import ExtractionCache
from app.services.loader.document_loaders import build_default_loader_registry
from app.services.vector_store import VectorStoreService


logger = logging.getLogger(__name__)


class DocumentProcessor:
    def __init__(
        self,
        *,
        cache: ExtractionCache | None = None,
        loader_registry=None,
        vector_store: VectorStoreService | None = None,
        callback_client: CallbackClient | None = None,
    ) -> None:
        self.cache = cache or ExtractionCache()
        self.loader_registry = loader_registry or build_default_loader_registry()
        self.vector_store = vector_store or VectorStoreService()
        self.callback_client = callback_client or CallbackClient()

    def process(self, request: DocumentProcessRequest) -> DocumentProcessResult:
        file_path = Path(request.filePath)
        file_hash = ""
        used_cache = False
        try:
            self._validate_request(request, file_path)
            file_hash = self.cache.compute_file_hash(str(file_path))
            cached = self.cache.get_cached_extraction(file_hash)
            if cached:
                used_cache = True
                pages = cached
            else:
                extracted = self._load_document(file_path, request.fileName)
                pages = [
                    ExtractedPage(page_number=page["page_number"], text=str(page["text"]))
                    for page in self._group_blocks_by_page(extracted)
                ]
                if pages:
                    self.cache.save_cached_extraction(file_hash, request.documentId, request.fileName, pages)
                else:
                    logger.warning(
                        "Document extraction returned no pages for jobId=%s file=%s; skipping cache write",
                        request.jobId,
                        request.filePath,
                    )

            chunk_records = self._chunk_pages_fixed(pages)
            self.vector_store.save_mock_vector_records(request, file_hash, chunk_records)

            result = DocumentProcessResult(
                jobId=request.jobId,
                status="READY",
                chunkCount=len(chunk_records),
                pageCount=len(pages),
                usedExtractionCache=used_cache,
                fileHash=file_hash,
                debugPages=self._build_debug_pages(pages),
                errorMessage=None,
            )
            self._send_callback(request.callbackUrl, result)
            return result
        except Exception as exc:
            logger.exception("Document processing failed for jobId=%s", request.jobId)
            result = DocumentProcessResult(
                jobId=request.jobId,
                status="FAILED",
                chunkCount=0,
                pageCount=0,
                usedExtractionCache=used_cache,
                fileHash=file_hash,
                debugPages=[],
                errorMessage=str(exc),
            )
            self._send_callback(request.callbackUrl, result)
            return result

    def _validate_request(self, request: DocumentProcessRequest, file_path: Path) -> None:
        if not request.jobId.strip():
            raise ValueError("jobId is required")
        if not request.documentId.strip():
            raise ValueError("documentId is required")
        if not request.workspaceId.strip():
            raise ValueError("workspaceId is required")
        if not request.userId.strip():
            raise ValueError("userId is required")
        if not request.fileName.strip():
            raise ValueError("fileName is required")
        if not request.filePath.strip():
            raise ValueError("filePath is required")
        if not request.callbackUrl.strip():
            raise ValueError("callbackUrl is required")
        if not file_path.exists():
            raise FileNotFoundError(f"File not found: {request.filePath}")

        normalized_type = self._normalize_file_type(request.fileType)
        normalized_suffix = file_path.suffix.lower().lstrip(".")
        if normalized_type not in {"pdf", "docx", "doc"}:
            raise ValueError(f"Unsupported fileType: {request.fileType}")
        if normalized_suffix not in {"pdf", "docx", "doc"}:
            raise ValueError(f"Unsupported file extension: {file_path.suffix}")
        if normalized_type != normalized_suffix:
            raise ValueError("fileType does not match filePath extension")

    def _load_document(self, file_path: Path, file_name: str) -> ExtractedDocument:
        loader = self.loader_registry.resolve(file_path)
        extracted = loader.load(file_path)
        if file_name and file_name.strip():
            extracted = extracted.__class__(
                source_path=extracted.source_path,
                title=Path(file_name).stem,
                file_type=extracted.file_type,
                blocks=extracted.blocks,
                metadata=extracted.metadata,
            )
        return extracted

    def _build_document_from_pages(
        self,
        file_path: Path,
        file_name: str,
        file_type: str,
        pages: list[ExtractedPage],
    ) -> ExtractedDocument:
        from app.models.knowledge_models import DocumentBlock

        blocks: list[DocumentBlock] = []
        for page_index, page in enumerate(pages, start=1):
            page_text = (page.text or "").strip()
            if not page_text:
                continue
            for block_index, block_text in enumerate(page_text.splitlines(), start=1):
                text = block_text.strip()
                if not text:
                    continue
                blocks.append(
                    DocumentBlock(
                        text=text,
                        kind="paragraph",
                        order=len(blocks) + 1,
                        page_number=page.page_number or page_index,
                        metadata={
                            "source": "cache",
                            "cached_block_index": block_index,
                        },
                    )
                )

        return ExtractedDocument(
            source_path=file_path,
            title=Path(file_name).stem if file_name else file_path.stem,
            file_type=file_type.lower().lstrip("."),
            blocks=blocks,
            metadata={
                "cache_source": True,
                "cached_page_count": len(pages),
            },
        )

    def _chunk_pages_fixed(self, pages: list[ExtractedPage], *, chunk_size: int = 4000, overlap: int = 500) -> list[ChunkRecord]:
        normalized_pages: list[tuple[int, str]] = []
        for index, page in enumerate(pages, start=1):
            text = " ".join((page.text or "").split()).strip()
            if not text:
                continue
            normalized_pages.append((page.page_number or index, text))

        if not normalized_pages:
            return []

        joined_text = "\n\n".join(text for _, text in normalized_pages)
        chunks: list[ChunkRecord] = []
        start = 0
        chunk_index = 1
        total_length = len(joined_text)

        while start < total_length:
            end = min(total_length, start + chunk_size)
            if end < total_length:
                whitespace = joined_text.rfind(" ", start, end)
                if whitespace > start:
                    end = whitespace

            chunk_text = joined_text[start:end].strip()
            if not chunk_text:
                break

            chunk_page_number = self._infer_chunk_page_number(normalized_pages, start, end, joined_text)
            chunks.append(
                ChunkRecord(
                    chunk_text=chunk_text,
                    chunk_index=chunk_index,
                    page_number=chunk_page_number,
                    article_number=None,
                    clause_number=None,
                    section_title=None,
                    unit_type="CHUNK",
                )
            )
            chunk_index += 1

            if end >= total_length:
                break
            start = max(0, end - overlap)

        return chunks

    def _infer_chunk_page_number(
        self,
        pages: list[tuple[int, str]],
        start: int,
        end: int,
        joined_text: str,
    ) -> int | None:
        cursor = 0
        for page_number, page_text in pages:
            page_start = cursor
            page_end = cursor + len(page_text)
            if page_start <= start < page_end:
                return page_number
            if page_start < end <= page_end:
                return page_number
            cursor = page_end + 2
        return pages[0][0] if pages else None

    def _group_blocks_by_page(self, document: ExtractedDocument) -> list[dict[str, object]]:
        pages: dict[int, list[str]] = {}
        for block in document.blocks:
            page = block.page_number or 1
            pages.setdefault(page, []).append(block.text)
        return [
            {"page_number": page_number, "text": "\n".join(texts).strip()}
            for page_number, texts in sorted(pages.items())
        ]

    def _build_debug_pages(self, pages: list[ExtractedPage]) -> list[PageDebugInfo]:
        debug_pages: list[PageDebugInfo] = []
        for index, page in enumerate(pages, start=1):
            text = (page.text or "").strip()
            debug_pages.append(
                PageDebugInfo(
                    page_number=page.page_number or index,
                    char_count=len(text),
                    text_preview=text[:180],
                )
            )
        return debug_pages

    def _send_callback(self, callback_url: str, result: DocumentProcessResult) -> None:
        if not self.callback_client.post_json(callback_url, result.model_dump()):
            logger.warning("Callback delivery failed for %s", callback_url)

    def _normalize_file_type(self, file_type: str) -> str:
        normalized = (file_type or "").strip().lower().lstrip(".")
        if normalized in {"pdf", "docx", "doc"}:
            return normalized
        if "pdf" in normalized:
            return "pdf"
        if "msword" in normalized or normalized == "doc":
            return "doc"
        if "wordprocessingml.document" in normalized or "docx" in normalized:
            return "docx"
        return normalized
