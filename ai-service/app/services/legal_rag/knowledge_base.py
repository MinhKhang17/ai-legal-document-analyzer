from __future__ import annotations

import json
import math
import re
import unicodedata
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path
from typing import Iterable, Sequence
import xml.etree.ElementTree as ET

from app.core.config import settings
from app.graph.repository import GraphRepository
from app.services.document_embedding_service import HashingEmbeddingProvider

WORD_XML_NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}


def strip_accents(text: str) -> str:
    normalized = unicodedata.normalize("NFKD", text)
    return "".join(char for char in normalized if not unicodedata.combining(char))


def normalize_text(text: str) -> str:
    text = strip_accents(text).lower()
    text = re.sub(r"[^a-z0-9_ ]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def tokenize(text: str) -> list[str]:
    return [token for token in normalize_text(text).split() if token]


def _is_structural_heading(line: str) -> bool:
    normalized = line.strip()
    if not normalized:
        return False
    if normalized in {"ONTOLOGY", "TAXONOMY"}:
        return True
    return bool(re.fullmatch(r"[A-Z0-9][A-Z0-9 ./-]{1,60}", normalized))


def load_document_paragraphs(source_path: Path) -> list[str]:
    if zipfile.is_zipfile(source_path):
        with zipfile.ZipFile(source_path) as archive:
            xml_bytes = archive.read("word/document.xml")
        root = ET.fromstring(xml_bytes)
        paragraphs: list[str] = []
        for paragraph in root.findall(".//w:body/w:p", WORD_XML_NS):
            texts = [node.text for node in paragraph.findall(".//w:t", WORD_XML_NS) if node.text]
            if texts:
                paragraphs.append("".join(texts).strip())
        return [paragraph for paragraph in paragraphs if paragraph]

    text = source_path.read_text(encoding="utf-8", errors="ignore")
    return [line.strip() for line in text.splitlines()]


@dataclass(frozen=True)
class RiskConcept:
    concept_id: str
    severity: str
    category: str
    aliases: tuple[str, ...] = ()
    source_file: str = ""

    @property
    def display_name(self) -> str:
        return self.concept_id.replace("_", " ")

    @property
    def normalized_aliases(self) -> tuple[str, ...]:
        aliases = {normalize_text(self.concept_id)}
        aliases.update(normalize_text(alias) for alias in self.aliases if alias.strip())
        return tuple(sorted(alias for alias in aliases if alias))


@dataclass(frozen=True)
class TaxonomyEntry:
    taxonomy_id: str
    aliases: tuple[str, ...] = ()

    @property
    def normalized_aliases(self) -> tuple[str, ...]:
        aliases = {normalize_text(self.taxonomy_id)}
        aliases.update(normalize_text(alias) for alias in self.aliases if alias.strip())
        return tuple(sorted(alias for alias in aliases if alias))


@dataclass(frozen=True)
class RiskKnowledgeBase:
    concepts: tuple[RiskConcept, ...]
    taxonomy: tuple[TaxonomyEntry, ...]
    source_files: tuple[str, ...]

    def concepts_by_id(self) -> dict[str, RiskConcept]:
        return {concept.concept_id: concept for concept in self.concepts}


@dataclass(frozen=True)
class RiskCandidate:
    concept: RiskConcept
    rule_score: float
    bm25_score: float
    semantic_score: float
    combined_score: float
    matched_aliases: tuple[str, ...] = ()
    evidence: tuple[str, ...] = ()


@dataclass(frozen=True)
class TaxonomyMatch:
    taxonomy_id: str
    confidence: float
    evidence: tuple[str, ...] = ()


@dataclass(frozen=True)
class _ConceptDocument:
    concept: RiskConcept
    document_text: str
    tokens: tuple[str, ...]
    embedding: tuple[float, ...]


class KnowledgeBaseParser:
    def parse(self, source_path: Path) -> RiskKnowledgeBase:
        paragraphs = [normalize for normalize in load_document_paragraphs(source_path) if normalize]
        concepts: list[RiskConcept] = []
        taxonomy: list[TaxonomyEntry] = []

        current_category = ""
        current_concept: dict[str, object] | None = None
        in_taxonomy = False
        i = 0

        while i < len(paragraphs):
            raw_line = paragraphs[i].strip()
            line = raw_line.upper()

            if not raw_line:
                i += 1
                continue

            if _is_structural_heading(line):
                if line == "TAXONOMY":
                    in_taxonomy = True
                elif line not in {"ONTOLOGY", "TAXONOMY"}:
                    current_category = raw_line.strip().upper()
                i += 1
                continue

            if in_taxonomy:
                if re.fullmatch(r"[a-z0-9_]+", raw_line):
                    taxonomy.append(TaxonomyEntry(taxonomy_id=raw_line))
                i += 1
                continue

            if re.fullmatch(r"[a-z0-9_]+", raw_line):
                if current_concept is not None:
                    concepts.append(self._finalize_concept(current_concept))
                current_concept = {
                    "concept_id": raw_line,
                    "severity": "",
                    "category": current_category,
                    "aliases": [],
                }
                i += 1
                continue

            if current_concept is None:
                i += 1
                continue

            if raw_line.startswith("Severity:"):
                current_concept["severity"] = raw_line.split(":", 1)[1].strip().upper()
                i += 1
                continue

            if raw_line.startswith("Aliases:"):
                alias_values: list[str] = []
                i += 1
                while i < len(paragraphs):
                    alias_line = paragraphs[i].strip()
                    if not alias_line:
                        i += 1
                        continue
                    if (
                        alias_line.startswith("Severity:")
                        or alias_line.startswith("Aliases:")
                        or alias_line.upper() in {"ONTOLOGY", "TAXONOMY"}
                        or re.fullmatch(r"[a-z0-9_]+", alias_line)
                    ):
                        break
                    alias_values.append(alias_line)
                    i += 1
                current_concept.setdefault("aliases", []).extend(alias_values)  # type: ignore[assignment]
                continue

            i += 1

        if current_concept is not None:
            concepts.append(self._finalize_concept(current_concept))

        return RiskKnowledgeBase(
            concepts=tuple(concepts),
            taxonomy=tuple(taxonomy),
            source_files=(source_path.name,),
        )

    def _finalize_concept(self, payload: dict[str, object]) -> RiskConcept:
        aliases = tuple(str(alias).strip() for alias in payload.get("aliases", []) if str(alias).strip())
        severity = str(payload.get("severity") or "UNKNOWN").upper()
        concept_id = str(payload["concept_id"])
        category = str(payload.get("category") or "")
        return RiskConcept(
            concept_id=concept_id,
            severity=severity,
            category=category,
            aliases=aliases,
        )


class BM25Index:
    def __init__(self, documents: Sequence[_ConceptDocument]) -> None:
        self._documents = list(documents)
        self._document_count = len(self._documents)
        self._avg_doc_len = (
            sum(len(document.tokens) for document in self._documents) / self._document_count
            if self._document_count
            else 0.0
        )
        self._doc_freqs: dict[str, int] = defaultdict(int)
        for document in self._documents:
            for token in set(document.tokens):
                self._doc_freqs[token] += 1

    def search(self, query: str, top_k: int = 5) -> list[tuple[RiskConcept, float]]:
        query_tokens = tokenize(query)
        if not query_tokens or not self._documents:
            return []

        scores: list[tuple[RiskConcept, float]] = []
        for document in self._documents:
            score = self._score_document(query_tokens, document.tokens)
            if score > 0:
                scores.append((document.concept, score))

        scores.sort(key=lambda item: item[1], reverse=True)
        return scores[:top_k]

    def _score_document(self, query_tokens: Sequence[str], document_tokens: Sequence[str]) -> float:
        if not query_tokens or not document_tokens:
            return 0.0

        term_freq = Counter(document_tokens)
        score = 0.0
        total_len = len(document_tokens)
        k1 = 1.5
        b = 0.75

        for token in query_tokens:
            if token not in term_freq:
                continue
            df = self._doc_freqs.get(token, 0)
            if not df:
                continue
            idf = math.log(1 + (self._document_count - df + 0.5) / (df + 0.5))
            numerator = term_freq[token] * (k1 + 1)
            denominator = term_freq[token] + k1 * (1 - b + b * total_len / max(self._avg_doc_len, 1.0))
            score += idf * (numerator / denominator)

        return score


class RiskKbSearchIndex:
    def __init__(
        self,
        knowledge_base: RiskKnowledgeBase,
        embedding_provider: HashingEmbeddingProvider | None = None,
    ) -> None:
        self.knowledge_base = knowledge_base
        self.embedding_provider = embedding_provider or HashingEmbeddingProvider()
        self._documents = self._build_documents()
        self._bm25 = BM25Index(self._documents)
        self._rule_engine = RuleEngine(knowledge_base)
        self._taxonomy_mapper = TaxonomyMapper(knowledge_base.taxonomy)

    def search(self, query: str, top_k: int = 5) -> list[RiskCandidate]:
        if not query.strip():
            return []

        rule_hits = self._rule_engine.match(query, top_k=max(top_k, 5))
        bm25_hits = self._bm25.search(query, top_k=max(top_k, 8))
        semantic_hits = self._semantic_search(query, top_k=max(top_k, 8))

        merged: dict[str, dict[str, object]] = {}
        for concept, score in rule_hits:
            merged[concept.concept_id] = {
                "concept": concept,
                "rule_score": score,
                "bm25_score": 0.0,
                "semantic_score": 0.0,
                "matched_aliases": tuple(self._rule_engine.matched_aliases(query, concept)),
            }

        for concept, score in bm25_hits:
            payload = merged.setdefault(
                concept.concept_id,
                {
                    "concept": concept,
                    "rule_score": 0.0,
                    "bm25_score": 0.0,
                    "semantic_score": 0.0,
                    "matched_aliases": (),
                },
            )
            payload["bm25_score"] = max(float(payload["bm25_score"]), self._normalize_bm25(score, bm25_hits))

        for concept, score in semantic_hits:
            payload = merged.setdefault(
                concept.concept_id,
                {
                    "concept": concept,
                    "rule_score": 0.0,
                    "bm25_score": 0.0,
                    "semantic_score": 0.0,
                    "matched_aliases": (),
                },
            )
            payload["semantic_score"] = max(float(payload["semantic_score"]), score)

        candidates: list[RiskCandidate] = []
        for payload in merged.values():
            concept = payload["concept"]  # type: ignore[assignment]
            rule_score = float(payload["rule_score"])
            bm25_score = float(payload["bm25_score"])
            semantic_score = float(payload["semantic_score"])
            combined_score = self._combine_scores(rule_score, bm25_score, semantic_score)
            evidence = self._build_evidence(query, concept)
            candidates.append(
                RiskCandidate(
                    concept=concept,
                    rule_score=rule_score,
                    bm25_score=bm25_score,
                    semantic_score=semantic_score,
                    combined_score=combined_score,
                    matched_aliases=tuple(payload["matched_aliases"]),  # type: ignore[arg-type]
                    evidence=tuple(evidence),
                )
            )

        candidates.sort(key=lambda item: item.combined_score, reverse=True)
        return candidates[:top_k]

    def map_taxonomy(self, clause_text: str, concept: RiskConcept | None = None) -> TaxonomyMatch | None:
        return self._taxonomy_mapper.match(clause_text, concept)

    def _build_documents(self) -> list[_ConceptDocument]:
        documents: list[_ConceptDocument] = []
        for concept in self.knowledge_base.concepts:
            document_text = " ".join(
                [
                    concept.concept_id,
                    concept.display_name,
                    concept.category,
                    concept.severity,
                    " ".join(concept.aliases),
                ]
            ).strip()
            tokens = tuple(tokenize(document_text))
            embedding = tuple(self.embedding_provider.embed([document_text])[0])
            documents.append(
                _ConceptDocument(
                    concept=concept,
                    document_text=document_text,
                    tokens=tokens,
                    embedding=embedding,
                )
            )
        return documents

    def _semantic_search(self, query: str, top_k: int = 5) -> list[tuple[RiskConcept, float]]:
        query_embedding = self.embedding_provider.embed([query])[0]
        query_vector = tuple(query_embedding)
        scores: list[tuple[RiskConcept, float]] = []

        for document in self._documents:
            score = _cosine_similarity(query_vector, document.embedding)
            if score > 0:
                scores.append((document.concept, score))

        scores.sort(key=lambda item: item[1], reverse=True)
        return scores[:top_k]

    def _normalize_bm25(self, score: float, hits: Sequence[tuple[RiskConcept, float]]) -> float:
        if not hits:
            return 0.0
        best = max(hit_score for _, hit_score in hits) or 1.0
        return min(1.0, score / best)

    def _combine_scores(self, rule_score: float, bm25_score: float, semantic_score: float) -> float:
        return min(1.0, (0.5 * rule_score) + (0.25 * bm25_score) + (0.25 * semantic_score))

    def _build_evidence(self, query: str, concept: RiskConcept) -> list[str]:
        evidence: list[str] = []
        normalized_query = normalize_text(query)
        for alias in concept.normalized_aliases:
            if alias and alias in normalized_query:
                evidence.append(alias)
        return evidence


class RuleEngine:
    def __init__(self, knowledge_base: RiskKnowledgeBase) -> None:
        self.knowledge_base = knowledge_base

    def match(self, clause_text: str, top_k: int = 5) -> list[tuple[RiskConcept, float]]:
        normalized_text = normalize_text(clause_text)
        tokens = set(tokenize(clause_text))
        matches: list[tuple[RiskConcept, float]] = []

        for concept in self.knowledge_base.concepts:
            score = self._score_concept(concept, normalized_text, tokens)
            if score > 0:
                matches.append((concept, score))

        matches.sort(key=lambda item: item[1], reverse=True)
        return matches[:top_k]

    def matched_aliases(self, clause_text: str, concept: RiskConcept) -> list[str]:
        normalized_text = normalize_text(clause_text)
        return [alias for alias in concept.normalized_aliases if alias in normalized_text]

    def _score_concept(self, concept: RiskConcept, normalized_text: str, tokens: set[str]) -> float:
        if concept.concept_id in normalized_text:
            return 1.0

        matched_aliases = self.matched_aliases(normalized_text, concept)
        if matched_aliases:
            return min(0.98, 0.85 + (0.03 * len(matched_aliases)))

        concept_tokens = set(tokenize(concept.concept_id))
        if concept_tokens and concept_tokens.issubset(tokens):
            return 0.9

        alias_token_overlap = 0.0
        for alias in concept.aliases:
            alias_tokens = set(tokenize(alias))
            if not alias_tokens:
                continue
            overlap = len(alias_tokens & tokens) / len(alias_tokens)
            alias_token_overlap = max(alias_token_overlap, overlap)

        if alias_token_overlap > 0.6:
            return min(0.82, 0.55 + alias_token_overlap * 0.35)

        return 0.0


class TaxonomyMapper:
    def __init__(self, taxonomy: Sequence[TaxonomyEntry]) -> None:
        self._taxonomy = list(taxonomy)

    def match(self, clause_text: str, concept: RiskConcept | None = None) -> TaxonomyMatch | None:
        normalized_text = normalize_text(clause_text)
        text_tokens = set(tokenize(clause_text))
        concept_tokens = set(tokenize(concept.concept_id)) if concept else set()
        concept_alias_tokens = set()
        if concept is not None:
            for alias in concept.aliases:
                concept_alias_tokens.update(tokenize(alias))

        best_match: TaxonomyMatch | None = None
        best_score = 0.0
        for entry in self._taxonomy:
            entry_tokens = set(tokenize(entry.taxonomy_id))
            if not entry_tokens:
                continue

            overlap_text = len(entry_tokens & text_tokens) / len(entry_tokens)
            overlap_concept = len(entry_tokens & concept_tokens) / len(entry_tokens) if concept_tokens else 0.0
            overlap_alias = len(entry_tokens & concept_alias_tokens) / len(entry_tokens) if concept_alias_tokens else 0.0
            substring_bonus = 1.0 if entry.taxonomy_id in normalized_text else 0.0
            score = max(overlap_text, overlap_concept, overlap_alias, substring_bonus)

            if score > best_score:
                best_score = score
                evidence = tuple(
                    token for token in sorted(entry_tokens) if token in text_tokens or token in concept_tokens or token in concept_alias_tokens
                )
                best_match = TaxonomyMatch(
                    taxonomy_id=entry.taxonomy_id,
                    confidence=score,
                    evidence=evidence,
                )

        if best_match is None or best_match.confidence < 0.2:
            return None
        return best_match


@lru_cache(maxsize=8)
def load_knowledge_base(docs_dir: Path | None = None) -> RiskKnowledgeBase:
    docs_root = docs_dir or settings.docs_flow_dir
    # Some deployments invoke the service from the monorepo root while the
    # bundled legal KB remains under ai-service/docs. Fall back only when the
    # caller-provided directory is absent, preserving every valid override.
    if not docs_root.exists() and settings.docs_flow_dir.exists():
        docs_root = settings.docs_flow_dir
    parser = KnowledgeBaseParser()

    source_files = [
        docs_root / settings.risk_kb_source_file,
    ]

    concepts: list[RiskConcept] = []
    taxonomy: list[TaxonomyEntry] = []
    discovered_sources: list[str] = []

    for source_file in source_files:
        if not source_file.exists():
            continue
        parsed = parser.parse(source_file)
        concepts.extend(parsed.concepts)
        taxonomy.extend(parsed.taxonomy)
        discovered_sources.extend(parsed.source_files)

    return RiskKnowledgeBase(
        concepts=tuple(concepts),
        taxonomy=tuple(taxonomy),
        source_files=tuple(discovered_sources),
    )


def load_knowledge_base_from_graph(repository: GraphRepository | None = None) -> RiskKnowledgeBase:
    repo = repository or GraphRepository(vector_index_name=settings.legal_vector_index_name)
    try:
        chunk_rows = repo.list_chunks()
    except Exception:
        return load_knowledge_base()

    if not chunk_rows:
        return load_knowledge_base()

    concepts: list[RiskConcept] = []
    source_files: set[str] = set()

    for row in chunk_rows:
        metadata_json = row.get("metadata_json") or "{}"
        try:
            metadata = json.loads(metadata_json) if isinstance(metadata_json, str) else {}
        except Exception:
            metadata = {}

        source_path = str(row.get("source_path") or metadata.get("source_path") or "").strip()
        file_type = str(row.get("file_type") or metadata.get("file_type") or "").strip()
        title = str(row.get("title") or "").strip() or Path(source_path).stem or str(row.get("chunk_id") or "").strip()
        text = str(row.get("text") or "").strip()
        if not text:
            continue

        source_file = Path(source_path).name if source_path else ""
        if source_file:
            source_files.add(source_file)

        severity = str(
            metadata.get("severity")
            or metadata.get("risk_severity")
            or metadata.get("level")
            or "HIGH"
        ).upper()
        category = str(
            metadata.get("knowledge_scope")
            or metadata.get("category")
            or metadata.get("document_type")
            or "neo4j_import"
        )

        aliases = tuple(
            alias
            for alias in dict.fromkeys(
                [
                    title,
                    source_file,
                    source_path,
                    text,
                ]
            )
            if alias
        )

        chunk_id = str(row.get("chunk_id") or title)
        concept_id = f"{title}::{chunk_id[:8]}" if title else chunk_id
        concepts.append(
            RiskConcept(
                concept_id=concept_id,
                severity=severity,
                category=f"{category}{f'/{file_type}' if file_type else ''}",
                aliases=aliases,
                source_file=source_file or source_path or "neo4j",
            )
        )

    if not concepts:
        return load_knowledge_base()

    return RiskKnowledgeBase(
        concepts=tuple(concepts),
        taxonomy=tuple(),
        source_files=tuple(sorted(source_files)) if source_files else ("neo4j:legal_chunk_embedding_index",),
    )


def _cosine_similarity(left: Sequence[float], right: Sequence[float]) -> float:
    if not left or not right:
        return 0.0

    numerator = sum(l * r for l, r in zip(left, right))
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    denominator = left_norm * right_norm
    if not denominator:
        return 0.0
    return max(0.0, min(1.0, numerator / denominator))
