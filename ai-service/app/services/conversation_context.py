from __future__ import annotations

import re
from dataclasses import asdict, dataclass, replace
from threading import RLock
from typing import Any

from app.services.retrieval_service import RagChunkHit


_FOLLOW_UP_MARKERS = (
    "ở trên",
    "câu trước",
    "phần trên",
    "điều đó",
    "điều khoản đó",
    "rủi ro đó",
    "dựa vào đâu",
    "dựa trên đâu",
    "bộ luật nào",
    "chứng cứ pháp lý nào",
    "căn cứ pháp lý nào",
    "citation nào",
    "nguồn nào",
    "vì sao",
    "tại sao",
    "nói rõ hơn",
    "chi tiết hơn",
    "giải thích lại",
    "sửa lại điều khoản",
    "sửa điều khoản đó",
    "rewrite that clause",
    "previous answer",
    "above",
)


@dataclass(frozen=True)
class UserSource:
    id: str
    type: str
    title: str | None
    clauseName: str | None
    content: str
    page: int | None
    section: str | None
    documentId: str | None = None


@dataclass(frozen=True)
class KbSource:
    id: str
    documentId: str | None
    title: str | None
    versionId: str | None
    effectiveDate: str | None
    chunkId: str
    excerpt: str
    isDirectlyApplicable: bool
    limitation: str | None
    score: float
    lawName: str | None
    lawCode: str | None
    articleNumber: str | None
    clauseNumber: str | None


@dataclass(frozen=True)
class ClaimLedgerEntry:
    claimId: str
    text: str
    basedOnUserSources: tuple[str, ...]
    basedOnKbSources: tuple[str, ...]
    legalBasisStrength: str
    isLegalConclusion: bool
    limitation: str | None


@dataclass(frozen=True)
class LastAnswerSummary:
    conclusion: str
    mainRisks: tuple[str, ...]
    recommendations: tuple[str, ...]
    citationsUsed: tuple[str, ...]
    legalLimitations: tuple[str, ...]


@dataclass(frozen=True)
class AnalysisSnapshot:
    sessionId: str
    analysisId: str
    contractType: str
    userRole: str
    jurisdiction: str
    riskLevel: str
    confidence: float | None
    responseStatus: str
    suggestionType: str
    userSources: tuple[UserSource, ...]
    kbSources: tuple[KbSource, ...]
    claimLedger: tuple[ClaimLedgerEntry, ...]
    lastAnswerSummary: LastAnswerSummary
    legalLimitations: tuple[str, ...]

    def to_prompt_payload(self) -> dict[str, Any]:
        return asdict(self)


class LegalConversationContextStore:
    """Process-local append-only snapshots keyed by chat session."""

    def __init__(self) -> None:
        self._snapshots: dict[str, list[AnalysisSnapshot]] = {}
        self._lock = RLock()

    def append(self, snapshot: AnalysisSnapshot) -> None:
        with self._lock:
            self._snapshots.setdefault(snapshot.sessionId, []).append(snapshot)

    def latest(self, session_id: str | None) -> AnalysisSnapshot | None:
        if not session_id:
            return None
        with self._lock:
            snapshots = self._snapshots.get(session_id)
            return snapshots[-1] if snapshots else None

    def all_for_session(self, session_id: str) -> tuple[AnalysisSnapshot, ...]:
        with self._lock:
            return tuple(self._snapshots.get(session_id, ()))


def is_follow_up_query(question: str) -> bool:
    normalized = re.sub(r"\s+", " ", question.casefold()).strip()
    return any(marker in normalized for marker in _FOLLOW_UP_MARKERS)


def build_analysis_snapshot(
    *,
    session_id: str,
    analysis_id: str,
    contract_type: str,
    user_role: str,
    jurisdiction: str,
    risk_level: str,
    confidence: float | None,
    response_status: str,
    suggestion_type: str,
    answer: str,
    analysis: dict[str, Any] | None,
    user_hits: list[RagChunkHit],
    knowledge_hits: list[RagChunkHit],
    used_user_ids: list[str],
    used_knowledge_ids: list[str],
) -> AnalysisSnapshot:
    user_sources = tuple(_to_user_source(hit) for hit in user_hits[:8])
    kb_sources = tuple(_to_kb_source(hit) for hit in knowledge_hits[:8])
    legal_limitations = tuple(_build_legal_limitations(kb_sources, used_knowledge_ids, response_status))
    claim_ledger = tuple(_build_claim_ledger(answer, user_sources, kb_sources))
    summary = _build_answer_summary(
        answer=answer,
        analysis=analysis,
        used_user_ids=used_user_ids,
        used_knowledge_ids=used_knowledge_ids,
        legal_limitations=legal_limitations,
    )
    return AnalysisSnapshot(
        sessionId=session_id,
        analysisId=analysis_id,
        contractType=contract_type,
        userRole=user_role,
        jurisdiction=jurisdiction,
        riskLevel=risk_level,
        confidence=confidence,
        responseStatus=response_status,
        suggestionType=suggestion_type,
        userSources=user_sources,
        kbSources=kb_sources,
        claimLedger=claim_ledger,
        lastAnswerSummary=summary,
        legalLimitations=legal_limitations,
    )


def snapshot_hits(
    snapshot: AnalysisSnapshot,
) -> tuple[list[RagChunkHit], list[RagChunkHit]]:
    user_hits = [
        RagChunkHit(
            citationId=source.id,
            sourceType="USER_DOCUMENT",
            score=1.0,
            chunkText=source.content,
            documentId=source.documentId,
            fileName=source.title,
            pageNumber=source.page,
            sectionTitle=source.section or source.clauseName,
        )
        for source in snapshot.userSources
    ]
    knowledge_hits = [
        RagChunkHit(
            citationId=source.id,
            sourceType="SYSTEM_KB",
            score=source.score,
            chunkText=source.excerpt,
            knowledgeDocumentId=source.documentId,
            lawName=source.lawName or source.title,
            lawCode=source.lawCode,
            articleNumber=source.articleNumber,
            clauseNumber=source.clauseNumber,
            rawChunkId=source.chunkId,
            metadata={
                "version_id": source.versionId,
                "effective_date": source.effectiveDate,
                "is_directly_applicable": source.isDirectlyApplicable,
            },
        )
        for source in snapshot.kbSources
    ]
    return user_hits, knowledge_hits


def merge_snapshot_with_current_hits(
    snapshot: AnalysisSnapshot,
    current_user_hits: list[RagChunkHit],
    current_knowledge_hits: list[RagChunkHit],
    allowed_document_ids: set[str] | None = None,
) -> tuple[list[RagChunkHit], list[RagChunkHit]]:
    snapshot_user_hits, snapshot_knowledge_hits = snapshot_hits(snapshot)
    if allowed_document_ids is not None:
        snapshot_user_hits = [
            hit for hit in snapshot_user_hits
            if hit.documentId is not None and hit.documentId in allowed_document_ids
        ]
    return (
        _merge_hits(snapshot_user_hits, current_user_hits, "USER"),
        _merge_hits(snapshot_knowledge_hits, current_knowledge_hits, "KB"),
    )


def _merge_hits(
    snapshot_items: list[RagChunkHit],
    current_items: list[RagChunkHit],
    prefix: str,
) -> list[RagChunkHit]:
    merged = list(snapshot_items)
    fingerprints = {_hit_fingerprint(hit) for hit in merged}
    for hit in current_items:
        fingerprint = _hit_fingerprint(hit)
        if fingerprint in fingerprints:
            continue
        merged.append(replace(hit, citationId=f"{prefix}-{len(merged) + 1}"))
        fingerprints.add(fingerprint)
    return merged


def _hit_fingerprint(hit: RagChunkHit) -> tuple[str, str, str]:
    return (
        hit.sourceType,
        hit.rawChunkId or hit.documentId or hit.knowledgeDocumentId or "",
        _compact(hit.chunkText, 400),
    )


def _to_user_source(hit: RagChunkHit) -> UserSource:
    source_type = "contract_clause" if hit.sectionTitle or hit.clauseNumber else "uploaded_document"
    return UserSource(
        id=hit.citationId,
        documentId=hit.documentId,
        type=source_type,
        title=hit.fileName or hit.title or None,
        clauseName=hit.sectionTitle or hit.clauseNumber,
        content=_compact(hit.chunkText, 1600),
        page=hit.pageNumber,
        section=hit.sectionTitle,
    )


def _to_kb_source(hit: RagChunkHit) -> KbSource:
    metadata = dict(hit.metadata or {})
    directly_applicable = _as_bool(
        metadata.get("is_directly_applicable") or metadata.get("isDirectlyApplicable")
    )
    limitation = _text(metadata.get("limitation"))
    if not directly_applicable and not limitation:
        limitation = "Retrieved KB evidence is not marked as directly applicable."
    return KbSource(
        id=hit.citationId,
        documentId=hit.knowledgeDocumentId or hit.documentId,
        title=hit.title or hit.lawName or hit.fileName or None,
        versionId=_text(metadata.get("version_id") or metadata.get("versionId")),
        effectiveDate=_text(metadata.get("effective_date") or metadata.get("effectiveDate")),
        chunkId=hit.rawChunkId or hit.citationId,
        excerpt=_compact(hit.chunkText, 1600),
        isDirectlyApplicable=directly_applicable,
        limitation=limitation,
        score=round(hit.score, 4),
        lawName=hit.lawName,
        lawCode=hit.lawCode,
        articleNumber=hit.articleNumber,
        clauseNumber=hit.clauseNumber,
    )


def _build_claim_ledger(
    answer: str,
    user_sources: tuple[UserSource, ...],
    kb_sources: tuple[KbSource, ...],
) -> list[ClaimLedgerEntry]:
    available_user = {source.id for source in user_sources}
    available_kb = {source.id: source for source in kb_sources}
    entries: list[ClaimLedgerEntry] = []
    blocks = re.split(r"(?<=[.!?])\s+|\n+", answer)
    for block in blocks:
        citation_ids = {item.upper() for item in re.findall(r"\[((?:KB|USER)-\d+)\]", block, re.I)}
        user_ids = tuple(sorted(item for item in citation_ids if item in available_user))
        kb_ids = tuple(sorted(item for item in citation_ids if item in available_kb))
        claim_text = _compact(re.sub(r"\[((?:KB|USER)-\d+)\]", "", block, flags=re.I), 600)
        if not claim_text:
            continue
        strength, limitation = _legal_basis_assessment(kb_ids, user_ids, available_kb)
        entries.append(
            ClaimLedgerEntry(
                claimId=f"CLAIM-{len(entries) + 1}",
                text=claim_text,
                basedOnUserSources=user_ids,
                basedOnKbSources=kb_ids,
                legalBasisStrength=strength,
                isLegalConclusion=_looks_like_legal_conclusion(claim_text),
                limitation=limitation,
            )
        )
    return entries[:12]


def _legal_basis_assessment(
    kb_ids: tuple[str, ...],
    user_ids: tuple[str, ...],
    available_kb: dict[str, KbSource],
) -> tuple[str, str | None]:
    if kb_ids:
        sources = [available_kb[item] for item in kb_ids]
        if any(source.isDirectlyApplicable for source in sources):
            return "DIRECT", None
        if max(source.score for source in sources) < 0.6:
            return "WEAK", "The cited KB evidence has weak retrieval relevance and is not marked directly applicable."
        return "INDIRECT", "The cited KB evidence is related but is not marked directly applicable."
    if user_ids:
        return "NONE", "This assessment is based only on user contract evidence, not direct legal KB evidence."
    return "NONE", "No mapped evidence supports this claim."


def _build_legal_limitations(
    kb_sources: tuple[KbSource, ...],
    used_knowledge_ids: list[str],
    response_status: str,
) -> list[str]:
    limitations: list[str] = []
    if not used_knowledge_ids:
        limitations.append("KB hiện tại chưa có căn cứ pháp lý trực tiếp để kết luận.")
    elif not any(source.isDirectlyApplicable for source in kb_sources if source.id in used_knowledge_ids):
        limitations.append("Các nguồn KB đã dùng chưa được đánh dấu là căn cứ áp dụng trực tiếp.")
    if response_status in {"LOW_CONFIDENCE", "OUT_OF_KNOWLEDGE_BASE"}:
        limitations.append(f"Phạm vi kết luận bị giới hạn vì responseStatus là {response_status}.")
    return limitations


def _build_answer_summary(
    *,
    answer: str,
    analysis: dict[str, Any] | None,
    used_user_ids: list[str],
    used_knowledge_ids: list[str],
    legal_limitations: tuple[str, ...],
) -> LastAnswerSummary:
    structured = analysis or {}
    if hasattr(structured, "model_dump"):
        structured = structured.model_dump()
    paragraphs = [_compact(item, 700) for item in re.split(r"\n+", answer) if item.strip()]
    conclusion = paragraphs[0] if paragraphs else ""
    risk_items = structured.get("riskItems") or structured.get("risk_items") or []
    main_risks = tuple(_summarize_item(item) for item in risk_items[:3])
    recommendations = structured.get("recommendations") or []
    compact_recommendations = tuple(_compact(str(item), 350) for item in recommendations[:3])
    return LastAnswerSummary(
        conclusion=conclusion,
        mainRisks=main_risks,
        recommendations=compact_recommendations,
        citationsUsed=tuple(dict.fromkeys([*used_user_ids, *used_knowledge_ids])),
        legalLimitations=legal_limitations,
    )


def _summarize_item(item: Any) -> str:
    if isinstance(item, dict):
        text = " - ".join(
            str(value).strip()
            for value in (item.get("title"), item.get("description"))
            if value
        )
        return _compact(text, 400)
    return _compact(str(item), 400)


def _looks_like_legal_conclusion(text: str) -> bool:
    lowered = text.casefold()
    return any(
        marker in lowered
        for marker in ("trái luật", "vô hiệu", "hợp pháp", "pháp luật", "quy định", "nghĩa vụ pháp lý")
    )


def _compact(value: str, limit: int) -> str:
    normalized = re.sub(r"\s+", " ", value).strip()
    return normalized if len(normalized) <= limit else normalized[: limit - 1].rstrip() + "…"


def _text(value: Any) -> str | None:
    normalized = str(value).strip() if value is not None else ""
    return normalized or None


def _as_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    return str(value or "").strip().casefold() in {"1", "true", "yes", "direct", "directly_applicable"}
