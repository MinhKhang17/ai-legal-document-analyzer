from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import app
from app.schemas import ChunkRecord, DocumentProcessRequest, ExtractedPage
from app.services.document_processor import DocumentProcessor
from app.services.extraction_cache import ExtractionCache


class _FakeVectorStore:
    def __init__(self) -> None:
        self.saved: list[tuple[str, str, list[ChunkRecord]]] = []

    def save_mock_vector_records(self, request, file_hash: str, chunks: list[ChunkRecord]):
        self.saved.append((request.documentId, file_hash, chunks))
        return SimpleNamespace(chunk_count=len(chunks))


class _FakeCallbackClient:
    def __init__(self) -> None:
        self.calls: list[dict[str, object]] = []

    def post_json(self, url: str, payload: dict[str, object], timeout_seconds: float = 30.0) -> bool:
        self.calls.append({"url": url, "payload": payload, "timeout_seconds": timeout_seconds})
        return True


class DocumentProcessingTests(unittest.TestCase):
    def test_extraction_cache_roundtrip(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            cache = ExtractionCache(cache_dir=Path(temp_dir))
            pages = [
                ExtractedPage(page_number=1, text="Điều 1. Phạm vi điều chỉnh"),
                ExtractedPage(page_number=2, text="Khoản 1. Nội dung"),
            ]
            cache.save_cached_extraction("abc123", "doc-1", "sample.pdf", pages)

            loaded = cache.get_cached_extraction("abc123")

        self.assertIsNotNone(loaded)
        self.assertEqual(len(loaded or []), 2)
        self.assertEqual((loaded or [])[0].page_number, 1)

    def test_document_processor_uses_fixed_chunking_on_cached_pages(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            file_path = Path(temp_dir) / "contract.pdf"
            file_path.write_bytes(b"%PDF-1.4 fake")

            fake_pages = [
                ExtractedPage(
                    page_number=1,
                    text="A" * 4500,
                )
            ]
            fake_cache = SimpleNamespace(
                compute_file_hash=lambda _: "file-hash-1",
                get_cached_extraction=lambda file_hash: fake_pages,
                save_cached_extraction=lambda *args, **kwargs: None,
            )
            fake_vector_store = _FakeVectorStore()
            fake_callback = _FakeCallbackClient()
            processor = DocumentProcessor(
                cache=fake_cache,
                vector_store=fake_vector_store,
                callback_client=fake_callback,
            )

            request = DocumentProcessRequest(
                jobId="job-1",
                documentId="doc-1",
                workspaceId="ws-1",
                userId="user-1",
                sourceType="USER_DOCUMENT",
                fileName="contract.pdf",
                fileType="pdf",
                filePath=str(file_path),
                callbackUrl="http://localhost/callback",
                contractType="RENTAL",
                contractTypeConfirmed=True,
            )
            result = processor.process(request)

        self.assertEqual(result.status, "READY")
        self.assertTrue(result.usedExtractionCache)
        self.assertGreater(result.chunkCount, 1)
        self.assertEqual(len(fake_vector_store.saved), 1)
        self.assertEqual(fake_callback.calls[0]["payload"]["status"], "READY")

    def test_internal_process_endpoint_returns_accepted(self) -> None:
        fake_processor = SimpleNamespace(process=lambda payload: None)
        with patch("app.api.internal_documents_api.get_processor", return_value=fake_processor):
            client = TestClient(app)
            with tempfile.TemporaryDirectory() as temp_dir:
                file_path = Path(temp_dir) / "contract.pdf"
                file_path.write_bytes(b"%PDF-1.4 fake")
                response = client.post(
                    "/internal/documents/process",
                    json={
                        "jobId": "job-1",
                        "documentId": "doc-1",
                        "workspaceId": "ws-1",
                        "userId": "user-1",
                        "sourceType": "USER_DOCUMENT",
                        "fileName": "contract.pdf",
                        "fileType": "pdf",
                        "filePath": str(file_path),
                        "callbackUrl": "http://localhost/callback",
                        "contractType": "RENTAL",
                        "contractTypeConfirmed": True,
                    },
                )

        self.assertEqual(response.status_code, 202)
        self.assertEqual(response.json(), {"jobId": "job-1", "documentId": "doc-1", "status": "ACCEPTED"})


if __name__ == "__main__":
    unittest.main()

