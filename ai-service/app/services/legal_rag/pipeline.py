from __future__ import annotations

import json
import logging
import re
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Protocol, Sequence

from fastapi import UploadFile

from app.core.config import settings
from app.models.knowledge_models import DocumentBlock, ExtractedDocument
from app.services.gemini_client import GeminiClient
from app.services.document_embedding_service import HashingEmbeddingProvider
from app.services.legal_rag.knowledge_base import (
    RiskCandidate,
    RiskConcept,
    RiskKbSearchIndex,
    TaxonomyMatch,
    load_knowledge_base,
    load_knowledge_base_from_graph,
    normalize_text,
)
from app.services.loader.document_loaders import build_default_loader_registry

logger = logging.getLogger(__name__)


class LegalBasisRepository(Protocol):
    def search(self, query: str, top_k: int = 5) -> list["LegalBasis"]:
        raise NotImplementedError


class LLMProvider(Protocol):
    def analyze(
        self,
        clause_text: str,
        candidates: Sequence[RiskCandidate],
        taxonomy: TaxonomyMatch | None,
    ) -> "LLMAnalysis | None":
        raise NotImplementedError


@dataclass(frozen=True)
class LegalBasis:
    source_id: str
    title: str
    content: str
    score: float
    source_type: str = ""
    metadata: dict[str, object] = field(default_factory=dict)


@dataclass(frozen=True)
class LLMAnalysis:
    concept_id: str
    confidence: float
    explanation: str
    taxonomy_id: str | None = None


@dataclass(frozen=True)
class ClauseFinding:
    clause_id: str
    title: str
    text: str
    taxonomy: str | None
    taxonomy_confidence: float
    risk_concept: str
    severity: str
    confidence: float
    explanation: str
    detection_method: str
    legal_basis: list[LegalBasis] = field(default_factory=list)
    candidates: list[RiskCandidate] = field(default_factory=list)
    llm_used: bool = False


@dataclass(frozen=True)
class ContractAnalysisSummary:
    clause_count: int
    finding_count: int
    high_risk_count: int
    medium_risk_count: int
    low_risk_count: int
    llm_used_count: int


@dataclass(frozen=True)
class ContractAnalysisReport:
    document_id: str
    filename: str
    title: str
    file_type: str
    source_path: str
    supported_formats: list[str]
    clauses: list[ClauseFinding]
    summary: ContractAnalysisSummary
    knowledge_source_files: tuple[str, ...]


@dataclass(frozen=True)
class ContractClause:
    clause_id: str
    title: str
    text: str
    order: int
    page_numbers: tuple[int, ...] = ()
    metadata: dict[str, object] = field(default_factory=dict)


class ContractClauseExtractor:
    CLAUSE_START_PATTERNS = (
        re.compile(r"^\s*(Điều|Khoản)\s+\d+(?:[.\-:]\s*.*)?$", re.IGNORECASE),
        re.compile(r"^\s*\d+(?:\.\d+)*\s+[A-ZĐÀÁẢÃẠĂÂÊÔƠƯ].*$"),
        re.compile(r"^\s*[A-ZĐÀÁẢÃẠĂÂÊÔƠƯ0-9][A-ZĐÀÁẢÃẠĂÂÊÔƠƯ0-9\s,./()\-]{4,}$"),
    )

    def extract(self, document: ExtractedDocument) -> list[ContractClause]:
        clauses: list[ContractClause] = []
        current_title = document.title
        current_parts: list[str] = []
        current_pages: list[int] = []
        current_metadata: dict[str, object] = {}

        def flush() -> None:
            nonlocal current_title, current_parts, current_pages, current_metadata
            text = "\n\n".join(part for part in current_parts if part.strip()).strip()
            if not text:
                current_title = document.title
                current_parts = []
                current_pages = []
                current_metadata = {}
                return

            clause_id = str(len(clauses) + 1)
            clauses.append(
                ContractClause(
                    clause_id=clause_id,
                    title=current_title or document.title,
                    text=text,
                    order=len(clauses) + 1,
                    page_numbers=tuple(dict.fromkeys(current_pages)),
                    metadata=dict(current_metadata),
                )
            )
            current_title = document.title
            current_parts = []
            current_pages = []
            current_metadata = {}

        for block in document.blocks:
            if not clauses and not current_parts and self._looks_like_contract_title(block.text):
                current_title = block.text.strip()
                current_metadata.update(block.metadata)
                continue

            if self._is_clause_start(block):
                flush()
                current_title = block.text.strip()
                current_parts = [block.text.strip()]
                current_pages = [block.page_number] if block.page_number is not None else []
                current_metadata = dict(block.metadata)
                continue

            if not current_parts and block.kind == "heading":
                current_title = block.text.strip()
                current_metadata = dict(block.metadata)
                continue

            if not current_parts and not self._looks_like_contract_title(block.text):
                current_title = block.text.strip()[:120]

            current_parts.append(block.text.strip())
            if block.page_number is not None:
                current_pages.append(block.page_number)
            current_metadata.update(block.metadata)

        flush()
        return clauses

    def _is_clause_start(self, block: DocumentBlock) -> bool:
        text = block.text.strip()
        if not text:
            return False
        if block.kind == "heading":
            return True
        return any(pattern.match(text) for pattern in self.CLAUSE_START_PATTERNS)

    def _looks_like_contract_title(self, text: str) -> bool:
        normalized = normalize_text(text)
        return len(normalized.split()) <= 12 and text.isupper()


class EmptyLegalBasisRepository:
    def search(self, query: str, top_k: int = 5) -> list[LegalBasis]:
        return []


class Neo4jLegalBasisRepository:
    def __init__(self, index_name: str | None = None) -> None:
        self._index_name = index_name or settings.legal_vector_index_name

    def search(self, query: str, top_k: int = 5) -> list[LegalBasis]:
        from app.graph.connection import get_driver

        cypher = """
        CALL db.index.vector.queryNodes($index_name, $top_k, $embedding)
        YIELD node, score
        RETURN node.node_id AS source_id,
               coalesce(node.title, '') AS title,
               coalesce(node.text, '') AS content,
               score AS score,
               coalesce(node.file_type, '') AS source_type,
               coalesce(node.metadata_json, '') AS metadata_json
        ORDER BY score DESC
        """

        embedding = HashingEmbeddingProvider().embed([query])[0]
        try:
            with get_driver().session() as session:
                records = session.run(
                    cypher,
                    index_name=self._index_name,
                    top_k=top_k,
                    embedding=embedding,
                )
                results: list[LegalBasis] = []
                for record in records:
                    metadata_json = record.get("metadata_json") or "{}"
                    try:
                        metadata = json.loads(metadata_json) if isinstance(metadata_json, str) else {}
                    except Exception:
                        metadata = {}
                    results.append(
                        LegalBasis(
                            source_id=str(record["source_id"]),
                            title=str(record["title"] or ""),
                            content=str(record["content"] or ""),
                            score=float(record["score"]),
                            source_type=str(record["source_type"] or ""),
                            metadata=metadata,
                        )
                    )
                return results[:top_k]
        except Exception as exc:
            logger.debug("Legal basis search skipped: %s", exc)
            return []


class DisabledLLMProvider:
    def analyze(
        self,
        clause_text: str,
        candidates: Sequence[RiskCandidate],
        taxonomy: TaxonomyMatch | None,
    ) -> LLMAnalysis | None:
        return None


class GeminiLLMProvider:
    def __init__(
        self,
        api_key: str,
        model: str,
        base_url: str = "https://generativelanguage.googleapis.com/v1beta",
        timeout_seconds: float = 30.0,
        max_output_tokens: int = 128,
        max_retries: int = 4,
        retry_backoff_seconds: float = 2.0,
    ) -> None:
        self.client = (
            GeminiClient(
                api_key=api_key,
                model=model,
                base_url=base_url,
                timeout_seconds=timeout_seconds,
                max_output_tokens=max_output_tokens,
                max_retries=max_retries,
                retry_backoff_seconds=retry_backoff_seconds,
            )
            if api_key and model
            else None
        )

    def analyze(
        self,
        clause_text: str,
        candidates: Sequence[RiskCandidate],
        taxonomy: TaxonomyMatch | None,
    ) -> LLMAnalysis | None:
        if self.client is None:
            return None

        payload = json.dumps(
            {
                "clause": _compact_whitespace(clause_text, max_chars=420),
                "taxonomy": None
                if taxonomy is None
                else {
                    "id": taxonomy.taxonomy_id,
                    "c": round(taxonomy.confidence, 3),
                },
                "candidates": [
                    {
                        "id": candidate.concept.concept_id,
                        "sev": candidate.concept.severity,
                        "r": round(candidate.rule_score, 3),
                        "s": round(max(candidate.semantic_score, candidate.bm25_score), 3),
                    }
                    for candidate in list(candidates)[:2]
                ],
            },
            ensure_ascii=False,
            separators=(",", ":"),
        )
        result = self.client.generate_text(
            system_prompt="Return compact JSON only: concept_id, taxonomy_id, confidence, explanation.",
            user_prompt=payload,
        )
        if result.error or not result.text:
            return None

        try:
            parsed = json.loads(_extract_json_payload(result.text))
        except json.JSONDecodeError:
            return None

        concept_id = str(parsed.get("concept_id") or "").strip()
        if not concept_id:
            return None

        return LLMAnalysis(
            concept_id=concept_id,
            confidence=float(parsed.get("confidence") or 0.0),
            explanation=str(parsed.get("explanation") or "").strip(),
            taxonomy_id=(str(parsed.get("taxonomy_id")).strip() or None),
        )


class ContractAnalysisService:
    def __init__(
        self,
        *,
        knowledge_base: RiskKnowledgeBase | None = None,
        legal_basis_repository: LegalBasisRepository | None = None,
        llm_provider: LLMProvider | None = None,
    ) -> None:
        self.loaders = build_default_loader_registry()
        self.clause_extractor = ContractClauseExtractor()
        self.embedding_provider = HashingEmbeddingProvider()
        self.knowledge_base = knowledge_base or load_knowledge_base_from_graph()
        self.search_index = RiskKbSearchIndex(self.knowledge_base, self.embedding_provider)
        self.legal_basis_repository = legal_basis_repository or Neo4jLegalBasisRepository()
        self.llm_provider = llm_provider or self._build_default_llm_provider()

    def supported_formats(self) -> list[str]:
        return self.loaders.supported_extensions()

    def analyze_file(self, source_path: str, title: str | None = None, filename: str | None = None) -> ContractAnalysisReport:
        path = Path(source_path)
        if not path.exists():
            raise FileNotFoundError(f"File not found: {source_path}")

        loader = self.loaders.resolve(path)
        extracted = loader.load(path)
        if title and title.strip():
            extracted = ExtractedDocument(
                source_path=extracted.source_path,
                title=title.strip(),
                file_type=extracted.file_type,
                blocks=extracted.blocks,
                metadata=extracted.metadata,
            )

        return self._analyze_document(extracted, filename=filename or path.name)

    async def analyze_upload(self, file: UploadFile, title: str | None = None) -> ContractAnalysisReport:
        suffix = Path(file.filename or "").suffix.lower()
        if suffix not in self.supported_formats():
            raise ValueError(f"Unsupported file type: {suffix or '[unknown]'}")

        temp_path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
                temp_path = Path(temp_file.name)
                while True:
                    chunk = await file.read(1024 * 1024)
                    if not chunk:
                        break
                    temp_file.write(chunk)

            return self.analyze_file(
                str(temp_path),
                title=title or Path(file.filename or "").stem,
                filename=file.filename,
            )
        finally:
            await file.close()
            if temp_path and temp_path.exists():
                try:
                    temp_path.unlink()
                except OSError:
                    logger.warning("Failed to remove temp file: %s", temp_path)

    def _analyze_document(self, extracted: ExtractedDocument, filename: str) -> ContractAnalysisReport:
        clauses = self.clause_extractor.extract(extracted)
        findings: list[ClauseFinding] = []
        llm_used_count = 0

        for clause in clauses:
            clause_finding = self._analyze_clause(clause)
            if clause_finding is not None:
                findings.append(clause_finding)
                if clause_finding.llm_used:
                    llm_used_count += 1

        severity_counts = {"HIGH": 0, "MEDIUM": 0, "LOW": 0}
        for finding in findings:
            severity = finding.severity.upper()
            if severity in severity_counts:
                severity_counts[severity] += 1

        summary = ContractAnalysisSummary(
            clause_count=len(clauses),
            finding_count=len(findings),
            high_risk_count=severity_counts["HIGH"],
            medium_risk_count=severity_counts["MEDIUM"],
            low_risk_count=severity_counts["LOW"],
            llm_used_count=llm_used_count,
        )

        logger.info(
            "Analyzed contract filename=%s clauses=%s findings=%s llm_used=%s",
            filename,
            summary.clause_count,
            summary.finding_count,
            summary.llm_used_count,
        )

        return ContractAnalysisReport(
            document_id=extracted.metadata.get("document_id", extracted.title),
            filename=filename,
            title=extracted.title,
            file_type=extracted.file_type,
            source_path=str(extracted.source_path),
            supported_formats=self.supported_formats(),
            clauses=findings,
            summary=summary,
            knowledge_source_files=self.knowledge_base.source_files,
        )

    def _analyze_clause(self, clause: ContractClause) -> ClauseFinding | None:
        candidates = self.search_index.search(clause.text, top_k=settings.max_llm_candidates)
        if not candidates:
            return None

        best_candidate = candidates[0]
        taxonomy = self.search_index.map_taxonomy(clause.text, best_candidate.concept)

        llm_result: LLMAnalysis | None = None
        detection_method = "rule_engine"
        if best_candidate.rule_score < settings.rule_confidence_threshold and best_candidate.combined_score < settings.llm_confidence_threshold:
            llm_result = self.llm_provider.analyze(clause.text, candidates, taxonomy)
            if llm_result is not None:
                detection_method = "llm"

        selected_candidate = best_candidate
        if llm_result is not None:
            matched = next(
                (candidate for candidate in candidates if candidate.concept.concept_id == llm_result.concept_id),
                None,
            )
            if matched is not None:
                selected_candidate = matched
            if llm_result.taxonomy_id:
                taxonomy = TaxonomyMatch(
                    taxonomy_id=llm_result.taxonomy_id,
                    confidence=max(taxonomy.confidence if taxonomy else 0.0, llm_result.confidence),
                    evidence=taxonomy.evidence if taxonomy else (),
                )

        legal_basis_query = self._build_legal_basis_query(selected_candidate.concept, taxonomy)
        legal_basis = self.legal_basis_repository.search(legal_basis_query, top_k=settings.legal_top_k)

        legal_evidence_score = self._score_legal_basis(legal_basis)
        llm_confidence = llm_result.confidence if llm_result is not None else 0.0
        confidence = self._combine_confidence(selected_candidate, legal_evidence_score, llm_confidence)
        if llm_result is not None:
            explanation = llm_result.explanation or self._build_explanation(clause.text, selected_candidate, taxonomy, legal_basis)
        else:
            explanation = self._build_explanation(clause.text, selected_candidate, taxonomy, legal_basis)

        if confidence < 0.25:
            return None

        return ClauseFinding(
            clause_id=clause.clause_id,
            title=clause.title,
            text=clause.text,
            taxonomy=taxonomy.taxonomy_id if taxonomy is not None else None,
            taxonomy_confidence=taxonomy.confidence if taxonomy is not None else 0.0,
            risk_concept=selected_candidate.concept.concept_id,
            severity=selected_candidate.concept.severity,
            confidence=confidence,
            explanation=explanation,
            detection_method=detection_method,
            legal_basis=legal_basis,
            candidates=candidates,
            llm_used=llm_result is not None,
        )

    def _build_legal_basis_query(self, concept: RiskConcept, taxonomy: TaxonomyMatch | None) -> str:
        query_parts = [
            concept.concept_id,
            concept.display_name,
            concept.category,
            " ".join(concept.aliases),
        ]
        if taxonomy is not None:
            query_parts.append(taxonomy.taxonomy_id)
        return " ".join(part for part in query_parts if part)

    def _score_legal_basis(self, legal_basis: Sequence[LegalBasis]) -> float:
        if not legal_basis:
            return 0.0
        best = max(item.score for item in legal_basis)
        return min(1.0, max(0.0, best))

    def _combine_confidence(
        self,
        candidate: RiskCandidate,
        legal_evidence_score: float,
        llm_confidence: float,
    ) -> float:
        combined = (
            0.4 * candidate.rule_score
            + 0.3 * max(candidate.semantic_score, candidate.bm25_score)
            + 0.2 * legal_evidence_score
            + 0.1 * llm_confidence
        )
        return round(min(1.0, combined), 4)

    def _build_explanation(
        self,
        clause_text: str,
        candidate: RiskCandidate,
        taxonomy: TaxonomyMatch | None,
        legal_basis: Sequence[LegalBasis],
    ) -> str:
        parts = [
            f"Matched risk concept '{candidate.concept.concept_id}'",
            f"severity={candidate.concept.severity}",
        ]
        if taxonomy is not None:
            parts.append(f"taxonomy={taxonomy.taxonomy_id} ({taxonomy.confidence:.2f})")
        if legal_basis:
            parts.append(f"legal_basis_hits={len(legal_basis)}")
        if candidate.matched_aliases:
            parts.append(f"aliases={', '.join(candidate.matched_aliases)}")
        return "; ".join(parts)

    def _build_default_llm_provider(self) -> LLMProvider:
        if settings.llm_provider.lower() == "gemini" and settings.gemini_api_key and settings.gemini_model:
            return GeminiLLMProvider(
                api_key=settings.gemini_api_key,
                model=settings.gemini_model,
                base_url=settings.gemini_base_url,
                timeout_seconds=settings.gemini_timeout_seconds,
                max_output_tokens=settings.gemini_max_output_tokens,
                max_retries=settings.gemini_max_retries,
                retry_backoff_seconds=settings.gemini_retry_backoff_seconds,
            )
        return DisabledLLMProvider()


def _extract_json_payload(text: str) -> str:
    compact = text.strip()
    if compact.startswith("```"):
        lines = compact.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].startswith("```"):
            lines = lines[:-1]
        compact = "\n".join(lines).strip()
    return compact


def _compact_whitespace(text: str, max_chars: int = 420) -> str:
    compact = " ".join(text.split()).strip()
    if len(compact) <= max_chars:
        return compact
    return compact[:max_chars].rsplit(" ", 1)[0].strip()
