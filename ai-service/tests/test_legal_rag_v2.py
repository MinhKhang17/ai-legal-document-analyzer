from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from app.models.knowledge_models import DocumentBlock, ExtractedDocument
from app.services.legal_rag.knowledge_base import load_knowledge_base, RiskKbSearchIndex
from app.services.legal_rag.pipeline import ContractAnalysisService, ContractClauseExtractor


class LegalRagV2Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.repo_root = Path(__file__).resolve().parents[2]
        cls.docs_dir = cls.repo_root / "docs" / "flow"
        cls.knowledge_base = load_knowledge_base(cls.docs_dir)

    def test_knowledge_base_loads_risk_concepts_from_docs(self) -> None:
        concept_ids = {concept.concept_id for concept in self.knowledge_base.concepts}
        self.assertIn("yeu_cau_dat_coc", concept_ids)
        self.assertIn("giu_cccd", concept_ids)
        self.assertGreaterEqual(len(concept_ids), 10)

    def test_search_index_returns_high_confidence_concept(self) -> None:
        index = RiskKbSearchIndex(self.knowledge_base)
        results = index.search("Nguoi lao dong phai ky quy 5 trieu va giu CCCD goc", top_k=3)
        self.assertTrue(results)
        self.assertIn(results[0].concept.concept_id, {"yeu_cau_dat_coc", "giu_cccd"})
        self.assertGreater(results[0].combined_score, 0.5)

    def test_clause_extractor_groups_blocks_into_clauses(self) -> None:
        extractor = ContractClauseExtractor()
        document = ExtractedDocument(
            source_path=self.repo_root / "sample.docx",
            title="Hop dong mau",
            file_type="docx",
            blocks=[
                DocumentBlock(text="DIEU 1. DAT COC", kind="heading", order=1),
                DocumentBlock(text="Nguoi lao dong phai ky quy 5 trieu.", kind="paragraph", order=2),
                DocumentBlock(text="DIEU 2. BAO MAT", kind="heading", order=3),
                DocumentBlock(text="CCCD ban goc phai duoc giao lai.", kind="paragraph", order=4),
            ],
        )

        clauses = extractor.extract(document)
        self.assertEqual(len(clauses), 2)
        self.assertIn("ky quy", clauses[0].text.lower())
        self.assertIn("cccd", clauses[1].text.lower())

    def test_supported_formats_include_docx(self) -> None:
        service = ContractAnalysisService()
        self.assertIn(".docx", service.supported_formats())
        self.assertIn(".pdf", service.supported_formats())


if __name__ == "__main__":
    unittest.main()
