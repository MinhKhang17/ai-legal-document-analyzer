from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from app.api.knowledge_api import AskRequest, ask_legal_knowledge, ask_legal_knowledge_v2
from app.api.risk_knowledge_api import QueryRequest, query_risk_knowledge
from app.models.knowledge_models import DocumentBlock, ExtractedDocument
from app.services.legal_rag.knowledge_base import load_knowledge_base_from_graph
from app.services.legal_rag.pipeline import ContractAnalysisService, LegalBasis
from app.services.loader.document_loaders import group_lines_into_blocks
from app.services.loader.text_document_loader import TextDocumentLoader
from app.services.risk_knowledge_service import RiskKnowledgeService, RiskKnowledgeServiceV2
from app.services.semantic_chunker import SemanticChunker, SemanticChunkerV2


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
                context=[{"type": "ancestors", "nodes": [{"title": "Bo luat mau"}, {"title": "Dieu 1"}]}],
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
                text="Nguoi lao dong phai nop 20.000.000 dong.",
                score=0.97,
                context=[
                    {
                        "type": "ancestors",
                        "nodes": [
                            {"title": "Bo luat TTTDS 2015"},
                            {"title": "Dieu 2. Dat coc"},
                        ],
                    }
                ],
            ),
            SimpleNamespace(
                chunk_id="legal_chunk_002",
                title="Khoan 1",
                text="Cong ty co quyen giu giay to goc trong thoi han hop dong.",
                score=0.91,
                context=[
                    {
                        "type": "ancestors",
                        "nodes": [
                            {"title": "Bo luat TTTDS 2015"},
                            {"title": "Dieu 2. Dat coc"},
                            {"title": "Khoan 1"},
                        ],
                    }
                ],
            ),
            SimpleNamespace(
                chunk_id="legal_chunk_003",
                title="Khoan 1",
                text="Cong ty co quyen giu giay to goc trong thoi han hop dong.",
                score=0.90,
                context=[
                    {
                        "type": "ancestors",
                        "nodes": [
                            {"title": "Bo luat TTTDS 2015"},
                            {"title": "Dieu 2. Dat coc"},
                            {"title": "Khoan 1"},
                        ],
                    }
                ],
            ),
        ]


class _FakeGeminiClient:
    def __init__(self) -> None:
        self.calls: list[dict[str, str]] = []

    def generate_text(self, *, system_prompt: str, user_prompt: str):
        self.calls.append({"system_prompt": system_prompt, "user_prompt": user_prompt})
        return SimpleNamespace(text="Tra loi tu LLM.", error=None)


class RiskKnowledgeImportTests(unittest.TestCase):
    def test_group_lines_into_blocks_splits_merged_legal_text(self) -> None:
        blocks = group_lines_into_blocks(
            [
                "HOP DONG LAO DONG",
                "Dieu 1. Ho so va giay toNguoi lao dong phai nop ban goc CCCD.Dieu 2. Dat coc va cam ketNguoi lao dong phai nop 20.000.000 dong.",
            ]
        )

        self.assertGreaterEqual(len(blocks), 1)
        self.assertTrue(any("Dieu 1" in block for block in blocks))
        self.assertTrue(any("Dieu 2" in block for block in blocks))

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
                DocumentBlock(text="Dieu 1. Ho so va giay to", kind="heading", order=2),
                DocumentBlock(text="Nguoi lao dong phai nop ban goc CCCD.", kind="paragraph", order=3),
                DocumentBlock(text="Khoan 1. Cong ty co quyen giu giay to goc.", kind="paragraph", order=4),
                DocumentBlock(text="Dieu 2. Dat coc va cam ket", kind="heading", order=5),
                DocumentBlock(text="Nguoi lao dong phai nop 20.000.000 dong.", kind="paragraph", order=6),
            ],
        )

        chunked = SemanticChunker().chunk_document(document)
        chunk_nodes = [node for node in chunked.nodes if node.label == "Chunk"]

        self.assertGreaterEqual(len(chunk_nodes), 2)
        self.assertTrue(any(node.metadata.get("chunk_type") in {"article", "clause", "point"} for node in chunk_nodes))
        self.assertTrue(all(node.metadata.get("embedding_text") for node in chunk_nodes))
        self.assertTrue(all(node.metadata.get("retrieval_text") for node in chunk_nodes))
        self.assertTrue(any("Dieu 1" in node.metadata.get("parent_path", "") for node in chunk_nodes))

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
                DocumentBlock(text="Dieu 1. Ho so va giay to", kind="heading", order=2),
                DocumentBlock(text=long_paragraph, kind="paragraph", order=3),
            ],
        )

        base_chunked = SemanticChunker().chunk_document(document)
        v2_chunked = SemanticChunkerV2().chunk_document(document)
        base_chunks = [node for node in base_chunked.nodes if node.label == "Chunk"]
        v2_chunks = [node for node in v2_chunked.nodes if node.label == "Chunk"]

        self.assertGreaterEqual(len(base_chunks), 1)
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

    def test_public_knowledge_ask_returns_retrieved_chunks(self) -> None:
        with patch("app.api.knowledge_api.get_service", return_value=_FakeLegalQueryService()):
            response = ask_legal_knowledge(AskRequest(query="dat coc", top_k=2))

        self.assertEqual(response.source, "neo4j://legal_chunk_embedding_index")
        self.assertTrue(response.answer_preview)
        self.assertEqual(response.chunks[0].chunk_id, "legal_chunk_001")
        self.assertIn("Nguoi lao dong phai nop 20.000.000 dong.", response.chunks[0].text)

    def test_public_knowledge_ask_v2_uses_llm_on_compact_chunk(self) -> None:
        fake_client = _FakeGeminiClient()

        with patch("app.api.knowledge_api.get_service", return_value=_FakeLegalQueryService()), patch(
            "app.api.knowledge_api._is_llm_v2_enabled",
            return_value=True,
        ), patch(
            "app.api.knowledge_api._build_gemini_client",
            return_value=fake_client,
        ):
            response = ask_legal_knowledge_v2(AskRequest(query="dat coc", top_k=2))

        self.assertEqual(response.answer, "Tra loi tu LLM.")
        self.assertEqual(response.llm_status, "ok")
        self.assertIsNone(response.llm_error)
        self.assertEqual(response.chunks[0].chunk_id, "legal_chunk_001")
        self.assertTrue(fake_client.calls)
        self.assertIn("You are a legal RAG assistant.", fake_client.calls[0]["system_prompt"])
        self.assertIn("Answer only using the provided context.", fake_client.calls[0]["system_prompt"])
        self.assertIn("Question:\ndat coc", fake_client.calls[0]["user_prompt"])
        self.assertIn("Context:", fake_client.calls[0]["user_prompt"])
        self.assertIn("[Source 1]", fake_client.calls[0]["user_prompt"])
        self.assertIn("Document:", fake_client.calls[0]["user_prompt"])
        self.assertIn("Location:", fake_client.calls[0]["user_prompt"])
        self.assertIn("Answer in Vietnamese.", fake_client.calls[0]["user_prompt"])

    def test_public_knowledge_ask_v2_can_be_disabled_via_env_flag(self) -> None:
        fake_client = _FakeGeminiClient()

        with patch("app.api.knowledge_api.get_service", return_value=_FakeLegalQueryService()), patch(
            "app.api.knowledge_api._is_llm_v2_enabled",
            return_value=False,
        ), patch("app.api.knowledge_api._build_gemini_client", return_value=fake_client):
            response = ask_legal_knowledge_v2(AskRequest(query="dat coc", top_k=2))

        self.assertEqual(response.llm_status, "disabled")
        self.assertEqual(response.llm_error, "LLM v2 is disabled by configuration")
        self.assertTrue(response.answer)
        self.assertEqual(fake_client.calls, [])

    def test_group_lines_into_blocks_strips_metadata_lines(self) -> None:
        blocks = group_lines_into_blocks(
            [
                "CONG BAO/SO 12",
                "Loai tai lieu: PDF",
                "Vi tri: Trang 1",
                "Dieu 1. Pham vi dieu chinh",
                "1. Noi dung chinh cua dieu luat.",
            ]
        )

        self.assertGreaterEqual(len(blocks), 1)
        self.assertTrue(all("Loai tai lieu" not in block for block in blocks))
        self.assertTrue(all("CONG BAO" not in block for block in blocks))

    def test_semantic_chunker_parses_vietnamese_legal_markers(self) -> None:
        document = ExtractedDocument(
            source_path=Path("C:/legal/sample.docx"),
            title="Bo luat mau",
            file_type="docx",
            blocks=[
                DocumentBlock(text="Phan thu nhat", kind="heading", order=1),
                DocumentBlock(text="Chuong I", kind="heading", order=2),
                DocumentBlock(text="Muc 1", kind="heading", order=3),
                DocumentBlock(text="Dieu 1. Pham vi dieu chinh", kind="heading", order=4),
                DocumentBlock(text="1. Co quan co tham quyen phai thuc hien dung quy trinh.", kind="paragraph", order=5),
                DocumentBlock(text="a) Ho so phai day du.", kind="paragraph", order=6),
                DocumentBlock(text="đ) Truong hop dac biet duoc xem xet theo quy dinh.", kind="paragraph", order=7),
            ],
        )

        chunked = SemanticChunker().chunk_document(document)
        chunk_nodes = [node for node in chunked.nodes if node.label == "Chunk"]
        labels = {node.label for node in chunked.nodes}

        self.assertTrue(chunk_nodes)
        self.assertIn("Part", labels)
        self.assertIn("Chapter", labels)
        self.assertIn("Section", labels)
        self.assertIn("Article", labels)
        self.assertTrue(any(node.metadata.get("chunk_type") == "clause" for node in chunk_nodes))
        self.assertTrue(any(node.metadata.get("chunk_type") == "point" for node in chunk_nodes))
        self.assertTrue(all(node.token_count <= 500 for node in chunk_nodes))
        self.assertTrue(all(node.metadata.get("parent_path") for node in chunk_nodes))

    def test_semantic_chunker_v2_splits_long_structural_chunks_smaller_again(self) -> None:
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
                DocumentBlock(text="Dieu 1. Ho so va giay to", kind="heading", order=2),
                DocumentBlock(text=long_paragraph, kind="paragraph", order=3),
            ],
        )

        base_chunked = SemanticChunker().chunk_document(document)
        v2_chunked = SemanticChunkerV2().chunk_document(document)
        base_chunks = [node for node in base_chunked.nodes if node.label == "Chunk"]
        v2_chunks = [node for node in v2_chunked.nodes if node.label == "Chunk"]

        self.assertGreaterEqual(len(base_chunks), 1)
        self.assertGreater(len(v2_chunks), 1)
        self.assertTrue(all(node.token_count <= 320 for node in v2_chunks))
        self.assertTrue(all(node.metadata.get("chunk_version") == 2 for node in v2_chunks))
        self.assertTrue(all(node.metadata.get("chunk_level") for node in v2_chunks))
        self.assertLessEqual(max(node.token_count for node in v2_chunks), max(node.token_count for node in base_chunks))


if __name__ == "__main__":
    unittest.main()
