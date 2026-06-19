"""API endpoint tìm tất cả lỗi sai trong hợp đồng thuê nhà/thuê đất."""
from __future__ import annotations

import json
import logging
from functools import lru_cache
from typing import Any

from fastapi import APIRouter, File, Form, UploadFile
from pydantic import BaseModel, ConfigDict, Field

from app.services.legal_rag.contract_error_detector import (
    ContractError,
    ContractErrorDetectionService,
    ErrorDetectionReport,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/v2/contracts", tags=["contracts-v2-errors"])


# ---------------------------------------------------------------------------
# Response Models
# ---------------------------------------------------------------------------

class ContractErrorResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    error_id: str
    category: str
    severity: str
    title: str
    description: str
    suggestion: str = ""
    clause_reference: str = ""
    legal_basis: str = ""
    confidence: float = 1.0


class ErrorSummaryResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    total_errors: int
    missing_clause_count: int
    format_error_count: int
    logical_error_count: int
    legal_risk_count: int
    high_count: int
    medium_count: int
    low_count: int


class ErrorDetectionResponse(BaseModel):
    document_id: str
    filename: str
    title: str
    file_type: str
    total_clauses: int
    errors: list[ContractErrorResponse]
    summary: ErrorSummaryResponse
    full_text_preview: str = ""


# ---------------------------------------------------------------------------
# Service Singleton
# ---------------------------------------------------------------------------

@lru_cache(maxsize=1)
def get_error_detection_service() -> ContractErrorDetectionService:
    return ContractErrorDetectionService()


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@router.post("/find-errors", response_model=ErrorDetectionResponse)
async def find_contract_errors(
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
) -> ErrorDetectionResponse:
    """Upload file hợp đồng (PDF/DOCX/TXT) và tìm tất cả lỗi sai.

    Phát hiện 4 loại lỗi:
    - **missing_clause**: Thiếu điều khoản bắt buộc theo BLDS 2015
    - **format_error**: Lỗi hình thức (thiếu ngày, CCCD, chữ ký...)
    - **logical_error**: Lỗi logic / mâu thuẫn nội dung (sử dụng AI)
    - **legal_risk**: Rủi ro pháp lý (điều khoản bất lợi, vi phạm luật)
    """
    service = get_error_detection_service()
    report: ErrorDetectionReport = await service.analyze_upload(file=file, title=title)

    response = ErrorDetectionResponse(
        document_id=report.document_id,
        filename=report.filename,
        title=report.title,
        file_type=report.file_type,
        total_clauses=report.total_clauses,
        errors=[
            ContractErrorResponse(
                error_id=error.error_id,
                category=error.category,
                severity=error.severity,
                title=error.title,
                description=error.description,
                suggestion=error.suggestion,
                clause_reference=error.clause_reference,
                legal_basis=error.legal_basis,
                confidence=error.confidence,
            )
            for error in report.errors
        ],
        summary=ErrorSummaryResponse(
            total_errors=report.summary.total_errors,
            missing_clause_count=report.summary.missing_clause_count,
            format_error_count=report.summary.format_error_count,
            logical_error_count=report.summary.logical_error_count,
            legal_risk_count=report.summary.legal_risk_count,
            high_count=report.summary.high_count,
            medium_count=report.summary.medium_count,
            low_count=report.summary.low_count,
        ),
        full_text_preview=report.full_text_preview,
    )

    logger.info(
        "find-errors response: filename=%s errors=%d",
        report.filename,
        report.summary.total_errors,
    )
    return response


@router.get("/find-errors/supported-formats")
def supported_formats() -> list[str]:
    """Trả về danh sách định dạng file được hỗ trợ."""
    return get_error_detection_service().supported_formats()
