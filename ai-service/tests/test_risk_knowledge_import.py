from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from app.api.risk_knowledge_api import QueryRequest, query_risk_knowledge
from app.api.knowledge_api import AskRequest, QueryRequest as LegalQueryRequest, ask_legal_knowledge, query_legal_knowledge
from app.models.knowledge_models import DocumentBlock, ExtractedDocument
from app.services.loader.document_loaders import group_lines_into_blocks
from app.services.loader.text_document_loader import TextDocumentLoader
from app.services.legal_rag.knowledge_base import load_knowledge_base_from_graph
from app.services.legal_rag.pipeline import ContractAnalysisService, LegalBasis
from app.services.semantic_chunker import SemanticChunker, SemanticChunkerV2
from app.services.risk_knowledge_service import RiskKnowledgeService, RiskKnowledgeServiceV2


class _FakeRiskRepository:
    def __init__(self) -> None:
        self.documents = []

    def upsert_document(self, document) -> None:
        self.documents.append(document)

    def search_chunks(self, query_embedding: list[float], top_k: int = 5):
        return []


class _FakeGraphRepository:
    def list_chunks(self):
        return [
            {
                "chunk_id": "risk_chunk_001",
                "title": "Dieu 1. Ho so va giay to",
                "text": "Nguoi lao dong phai nop ban goc CCCD va cong ty co quyen giu giay to goc.",
                "source_path": r"C:\\risk\\hop_dong_rui_ro_cao.docx",
                "file_type": "docx",
                "metadata_json": "{\"knowledge_scope\": \"risk_training\", \"severity\": \"HIGH\"}",
                "order": 1,
            }
        ]


class _FakeLegalBasisRepository:
    def search(self, query: str, top_k: int = 5):
        return [
            LegalBasis(
                source_id="risk_chunk_001",
                title="Dieu 1. Ho so va giay to",
                content="Nguoi lao dong phai nop ban goc CCCD va cong ty co quyen giu giay to goc.",
                score=0.99,
                source_type="docx",
                metadata={"knowledge_scope": "risk_training"},
            )
        ]


class _FakeQueryService:
    def search(self, query: str, top_k: int = 5):
        return [
            SimpleNamespace(
                chunk_id="risk_chunk_001",
                title="Dieu 1. Ho so va giay to",
                text="Nguoi lao dong phai nop ban goc CCCD va cong ty co quyen giu giay to goc.",
                score=0.98,
                context=[{"type": "lineage", "nodes": []}],
            )
        ]


class _CaptureSearchRepository:
    def __init__(self) -> None:
        self.calls: list[dict[str, object]] = []

    def search_chunks(self, query_embedding: list[float], top_k: int = 5, query_text: str | None = None, metadata_filter=None):
        self.calls.append(
            {
                "query_embedding": query_embedding,
                "top_k": top_k,
                "query_text": query_text,
                "metadata_filter": metadata_filter,
            }
        )
        return []


class _FakeLegalQueryService:
    def search(self, query: str, top_k: int = 5):
        return [
            SimpleNamespace(
                chunk_id="legal_chunk_001",
                title="Dieu 2. Dat coc",
                text="Loai tai lieu: hop_dong\nVi tri: Dieu 2. Dat coc\nTieu de: Dieu 2. Dat coc\nNoi dung: Nguoi lao dong phai nop 20.000.000 dong.",
                score=0.97,
                context=[{"type": "lineage", "nodes": []}],
            )
        ]


class RiskKnowledgeImportTests(unittest.TestCase):
    def test_group_lines_into_blocks_splits_merged_legal_text(self) -> None:
        blocks = group_lines_into_blocks(
            [
                "HOP DONG LAO DONG",
                "Điều 1. Hồ sơ và giấy tờNgười lao động phải nộp bản gốc CCCD.Điều 2. Đặt cọc và cam kếtNgười lao động phải nộp 20.000.000 đồng.",
            ]
        )

        self.assertGreaterEqual(len(blocks), 3)
        self.assertTrue(any("Điều 1" in block for block in blocks))
        self.assertTrue(any("Điều 2" in block for block in blocks))

    def test_text_loader_supports_txt_documents(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "risk_note.txt"
            path.write_text(
                "DIEU 1. DAT COC\nNguoi lao dong phai ky quy 5 trieu.\n\nDIEU 2. GIU CCCD\nCCCD goc phai duoc giao lai.",
                encoding="utf-8",
            )

            document = TextDocumentLoader().load(path)

            self.assertEqual(document.file_type, "txt")
            self.assertGreaterEqual(len(document.blocks), 2)
            self.assertIn("ky quy", document.blocks[0].text.lower())

    def test_semantic_chunker_prefers_contract_structure(self) -> None:
        document = ExtractedDocument(
            source_path=Path("C:/Users/ASUS/Downloads/VanBanGoc_BO LUAT 45 QH14.pdf"),
            title="Hop dong lao dong",
            file_type="pdf",
            blocks=[
                DocumentBlock(text="HOP DONG LAO DONG", kind="heading", order=1),
                DocumentBlock(text="Điều 1. Hồ sơ và giấy tờ", kind="heading", order=2),
                DocumentBlock(text="Người lao động phải nộp bản gốc CCCD.", kind="paragraph", order=3),
                DocumentBlock(text="Khoản 1. Công ty có quyền giữ giấy tờ gốc.", kind="paragraph", order=4),
                DocumentBlock(text="Điều 2. Đặt cọc và cam kết", kind="heading", order=5),
                DocumentBlock(text="Người lao động phải nộp 20.000.000 đồng.", kind="paragraph", order=6),
            ],
        )

        chunked = SemanticChunker().chunk_document(document)
        chunk_nodes = [node for node in chunked.nodes if node.label == "Chunk"]

        self.assertGreaterEqual(len(chunk_nodes), 2)
        self.assertTrue(any(node.metadata.get("chunk_level") == "article" for node in chunk_nodes))
        self.assertTrue(all(node.metadata.get("embedding_text") for node in chunk_nodes))
        self.assertTrue(all(node.metadata.get("retrieval_text") for node in chunk_nodes))
        self.assertTrue(any("Điều 1" in node.metadata.get("parent_path", "") for node in chunk_nodes))

    def test_semantic_chunker_v2_splits_long_structural_chunks_smaller(self) -> None:
        long_paragraph = " ".join(
            [
                "Nguoi lao dong phai nop ban goc CCCD va cong ty co quyen giu giay to goc.",
                "Nguyen tac nay ap dung khi hop dong co gia tri va co dieu khoan bo sung.",
            ]
            * 40
        )
        document = ExtractedDocument(
            source_path=Path("C:/Users/ASUS/Downloads/hop_dong_rui_ro_cao.docx"),
            title="Hop dong lao dong",
            file_type="docx",
            blocks=[
                DocumentBlock(text="HOP DONG LAO DONG", kind="heading", order=1),
                DocumentBlock(text="Điều 1. Hồ sơ và giấy tờ", kind="heading", order=2),
                DocumentBlock(text=long_paragraph, kind="paragraph", order=3),
            ],
        )

        base_chunked = SemanticChunker().chunk_document(document)
        v2_chunked = SemanticChunkerV2().chunk_document(document)
        base_chunks = [node for node in base_chunked.nodes if node.label == "Chunk"]
        v2_chunks = [node for node in v2_chunked.nodes if node.label == "Chunk"]

        self.assertEqual(len(base_chunks), 1)
        self.assertGreater(len(v2_chunks), 1)
        self.assertTrue(all(node.token_count <= 320 for node in v2_chunks))
        self.assertTrue(all(node.metadata.get("chunk_version") == 2 for node in v2_chunks))

    def test_risk_service_stores_training_metadata_in_neo4j_payload(self) -> None:
        fake_repo = _FakeRiskRepository()
        service = RiskKnowledgeService(repository=fake_repo)

        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "risk_sample.txt"
            path.write_text(
                "DIEU 1. DAT COC\nNguoi lao dong phai ky quy 5 trieu.",
                encoding="utf-8",
            )

            result = service.ingest_file(str(path), title="Risk sample", filename=path.name)

        self.assertEqual(result.title, "Risk sample")
        self.assertTrue(fake_repo.documents)
        stored = fake_repo.documents[0]
        self.assertEqual(stored.metadata["source_metadata"]["knowledge_scope"], "risk_training")
        self.assertEqual(stored.metadata["source_metadata"]["vector_index_name"], "legal_chunk_embedding_index")

    def test_contract_analysis_uses_imported_risk_chunks_from_neo4j(self) -> None:
        knowledge_base = load_knowledge_base_from_graph(_FakeGraphRepository())
        service = ContractAnalysisService(
            knowledge_base=knowledge_base,
            legal_basis_repository=_FakeLegalBasisRepository(),
        )

        document = ExtractedDocument(
            source_path=Path("C:/Users/ASUS/Downloads/hop_dong_rui_ro_cao.docx"),
            title="Hop dong lao dong",
            file_type="docx",
            blocks=[
                DocumentBlock(text="HOP DONG LAO DONG", kind="heading", order=1),
                DocumentBlock(
                    text="Nguoi lao dong phai nop ban goc CCCD va cong ty co quyen giu giay to goc.",
                    kind="paragraph",
                    order=2,
                ),
            ],
        )

        report = service._analyze_document(document, filename="hop_dong_rui_ro_cao.docx")

        self.assertGreaterEqual(report.summary.finding_count, 1)
        self.assertTrue(report.clauses)
        self.assertIn("Dieu 1. Ho so va giay to", report.clauses[0].risk_concept)
        self.assertTrue(report.clauses[0].legal_basis)
        self.assertEqual(report.clauses[0].legal_basis[0].source_id, "risk_chunk_001")

    def test_knowledge_service_v2_search_uses_chunk_version_filter(self) -> None:
        capture_repo = _CaptureSearchRepository()
        service = RiskKnowledgeServiceV2(repository=capture_repo)

        service.search("giu CCCD goc", top_k=4)

        self.assertTrue(capture_repo.calls)
        self.assertEqual(capture_repo.calls[0]["metadata_filter"], {"chunking_version": 2})
        self.assertEqual(capture_repo.calls[0]["top_k"], 4)

    def test_query_endpoint_returns_imported_chunks(self) -> None:
        with patch("app.api.risk_knowledge_api.get_service", return_value=_FakeQueryService()):
            response = query_risk_knowledge(QueryRequest(query="giu CCCD goc", top_k=3))

        self.assertEqual(response.source, "neo4j://legal_chunk_embedding_index")
        self.assertEqual(response.top_k, 3)
        self.assertTrue(response.answer_preview)
        self.assertEqual(response.chunks[0].chunk_id, "risk_chunk_001")
        self.assertIn("CCCD", response.chunks[0].text)

    def test_public_knowledge_query_returns_retrieved_chunks(self) -> None:
        with patch("app.api.knowledge_api.get_legal_service", return_value=_FakeLegalQueryService()):
            response = query_legal_knowledge(LegalQueryRequest(query="dat coc", top_k=2))

        self.assertEqual(response.source, "neo4j://legal_chunk_embedding_index")
        self.assertTrue(response.answer_preview)
        self.assertEqual(response.chunks[0].chunk_id, "legal_chunk_001")
        self.assertIn("Loai tai lieu", response.chunks[0].text)

    def test_public_knowledge_ask_uses_llm_response(self) -> None:
        with patch("app.api.knowledge_api.get_legal_service", return_value=_FakeLegalQueryService()), patch(
            "app.api.knowledge_api._call_llm",
            return_value="Theo CONTEXT, người lao động phải nộp 20.000.000 đồng.",
        ):
            response = ask_legal_knowledge(AskRequest(query="dat coc", top_k=2))

        self.assertEqual(response.answer, "Theo CONTEXT, người lao động phải nộp 20.000.000 đồng.")
        self.assertEqual(response.source, "neo4j://legal_chunk_embedding_index")
        self.assertEqual(response.chunks[0].chunk_id, "legal_chunk_001")


if __name__ == "__main__":
    unittest.main()
