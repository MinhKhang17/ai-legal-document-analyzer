from __future__ import annotations

from functools import lru_cache
from typing import Any

from fastapi import APIRouter, File, Form, UploadFile
from pydantic import BaseModel, ConfigDict, Field

from app.services.legal_rag.pipeline import (
    ClauseFinding,
    ContractAnalysisReport,
    ContractAnalysisService,
    LegalBasis,
)


class LegalBasisResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    source_id: str
    title: str
    content: str
    score: float
    source_type: str = ""
    metadata: dict[str, Any] = Field(default_factory=dict)


class ClauseFindingResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    clause_id: str
    title: str
    text: str
    taxonomy: str | None = None
    taxonomy_confidence: float = 0.0
    risk_concept: str
    severity: str
    confidence: float
    explanation: str
    detection_method: str
    llm_used: bool = False
    legal_basis: list[LegalBasisResponse] = Field(default_factory=list)


class SummaryResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    clause_count: int
    finding_count: int
    high_risk_count: int
    medium_risk_count: int
    low_risk_count: int
    llm_used_count: int


class ContractAnalysisResponse(BaseModel):
    document_id: str
    filename: str
    title: str
    file_type: str
    source_path: str
    supported_formats: list[str]
    clauses: list[ClauseFindingResponse]
    summary: SummaryResponse
    knowledge_source_files: list[str]


router = APIRouter(prefix="/v2/contracts", tags=["contracts-v2"])


@lru_cache(maxsize=1)
def get_service() -> ContractAnalysisService:
    return ContractAnalysisService()


def _map_legal_basis(item: LegalBasis) -> LegalBasisResponse:
    return LegalBasisResponse.model_validate(item)


def _map_clause_finding(item: ClauseFinding) -> ClauseFindingResponse:
    return ClauseFindingResponse(
        clause_id=item.clause_id,
        title=item.title,
        text=item.text,
        taxonomy=item.taxonomy,
        taxonomy_confidence=item.taxonomy_confidence,
        risk_concept=item.risk_concept,
        severity=item.severity,
        confidence=item.confidence,
        explanation=item.explanation,
        detection_method=item.detection_method,
        llm_used=item.llm_used,
        legal_basis=[_map_legal_basis(basis) for basis in item.legal_basis],
    )


@router.get("/supported-formats")
def supported_formats() -> list[str]:
    return get_service().supported_formats()


@router.post("/upload", response_model=ContractAnalysisResponse)
async def upload_contract(
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
) -> ContractAnalysisResponse:
    report: ContractAnalysisReport = await get_service().analyze_upload(file=file, title=title)
    return ContractAnalysisResponse(
        document_id=report.document_id,
        filename=report.filename,
        title=report.title,
        file_type=report.file_type,
        source_path=report.source_path,
        supported_formats=report.supported_formats,
        clauses=[_map_clause_finding(clause) for clause in report.clauses],
        summary=SummaryResponse.model_validate(report.summary),
        knowledge_source_files=list(report.knowledge_source_files),
    )
