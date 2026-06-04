"""Pydantic models for the Legal Analysis API.

Covers contract classification, single-contract analysis and contract
comparison. Enums mirror the legal knowledge-graph taxonomy so the Spring Boot
backend can rely on a stable, well-known vocabulary.
"""
from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


# --- Enums --------------------------------------------------------------------


class ContractType(str, Enum):
    HOUSE_PURCHASE = "HOUSE_PURCHASE"
    HOUSE_RENTAL = "HOUSE_RENTAL"
    LAND_TRANSFER = "LAND_TRANSFER"
    LAND_DEPOSIT = "LAND_DEPOSIT"
    SERVICE_CONTRACT = "SERVICE_CONTRACT"
    COMMERCIAL_CONTRACT = "COMMERCIAL_CONTRACT"
    OTHER = "OTHER"
    UNKNOWN = "UNKNOWN"


class RiskLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class Severity(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class DifferenceType(str, Enum):
    SAME = "SAME"
    MINOR_DIFFERENCE = "MINOR_DIFFERENCE"
    MATERIAL_DIFFERENCE = "MATERIAL_DIFFERENCE"
    CONFLICT = "CONFLICT"
    MISSING_IN_A = "MISSING_IN_A"
    MISSING_IN_B = "MISSING_IN_B"


class FavorableVersion(str, Enum):
    A = "A"
    B = "B"
    EQUAL = "EQUAL"
    INSUFFICIENT_DATA = "INSUFFICIENT_DATA"


class OutputMode(str, Enum):
    JSON = "JSON"
    TEXT = "TEXT"


# --- Shared options -----------------------------------------------------------


class AnalysisOptions(BaseModel):
    outputMode: OutputMode = OutputMode.JSON
    includeGraphContext: bool = True


# --- Classify -----------------------------------------------------------------


class ClassifyContractRequest(BaseModel):
    """Request để phân loại hợp đồng.
    
    Attributes:
        text: Văn bản hợp đồng cần phân loại (tối thiểu 10 ký tự, tối đa 100,000 ký tự)
    """
    
    text: str = Field(
        ...,
        min_length=10,
        max_length=100000,
        description="Văn bản hợp đồng cần phân loại",
        examples=[
            "HỢP ĐỒNG THUÊ NHÀ. Bên cho thuê: Nguyễn Văn A. Bên thuê: Trần Thị B. "
            "Đối tượng: căn hộ tại Hà Nội. Tiền thuê 10 triệu/tháng."
        ],
    )


class ClassifyContractResponse(BaseModel):
    contractType: ContractType = ContractType.UNKNOWN
    confidence: float = 0.0
    reason: str = ""
    detectedParties: List[str] = Field(default_factory=list)
    detectedImportantTerms: List[str] = Field(default_factory=list)


# --- Analyze ------------------------------------------------------------------


class AnalyzeContractRequest(BaseModel):
    """Request để phân tích rủi ro hợp đồng.
    
    Attributes:
        contractText: Văn bản hợp đồng cần phân tích
        contractType: Loại hợp đồng (để UNKNOWN để tự động phân loại)
        protectedParty: Bên cần bảo vệ (ví dụ: "bên thuê", "bên mua")
        question: Câu hỏi cụ thể về hợp đồng (tùy chọn)
        options: Tùy chọn phân tích
    """
    
    contractText: str = Field(
        ...,
        min_length=10,
        max_length=100000,
        description="Văn bản hợp đồng cần phân tích rủi ro",
        examples=[
            "HỢP ĐỒNG THUÊ NHÀ. Bên cho thuê: Nguyễn Văn A. Bên thuê: Trần Thị B. "
            "Đối tượng: căn hộ tại Hà Nội. Tiền thuê 10 triệu/tháng. Đặt cọc 2 tháng."
        ],
    )
    contractType: ContractType = Field(
        default=ContractType.UNKNOWN,
        description="Loại hợp đồng. Để UNKNOWN để API tự động phân loại.",
    )
    protectedParty: Optional[str] = Field(
        default=None,
        max_length=200,
        description="Bên cần bảo vệ quyền lợi (ví dụ: 'bên thuê', 'bên mua', 'người lao động')",
        examples=["bên thuê", "bên mua"],
    )
    question: Optional[str] = Field(
        default=None,
        max_length=500,
        description="Câu hỏi cụ thể về hợp đồng (tùy chọn)",
        examples=["Hợp đồng này có rủi ro gì?", "Điều khoản thanh toán có hợp lý không?"],
    )
    options: AnalysisOptions = Field(default_factory=AnalysisOptions)


class RiskItem(BaseModel):
    riskType: str
    severity: Severity = Severity.MEDIUM
    title: str
    explanation: str
    recommendation: str = ""
    requiresExpertReview: bool = False


class AnalyzeContractResponse(BaseModel):
    contractType: ContractType
    summary: str
    overallRiskLevel: RiskLevel = RiskLevel.MEDIUM
    riskItems: List[RiskItem] = Field(default_factory=list)
    missingClauses: List[str] = Field(default_factory=list)
    recommendations: List[str] = Field(default_factory=list)
    graphContextUsed: List[Dict[str, Any]] = Field(default_factory=list)
    llmUsed: bool = False
    fallback: bool = False


# --- Compare ------------------------------------------------------------------


class CompareContractsRequest(BaseModel):
    """Request để so sánh 2 hợp đồng.
    
    Attributes:
        documentAText: Văn bản hợp đồng A (thường là bản gốc/cũ)
        documentBText: Văn bản hợp đồng B (thường là bản mới/sửa đổi)
        contractType: Loại hợp đồng
        protectedParty: Bên cần bảo vệ để đánh giá phiên bản nào có lợi hơn
        options: Tùy chọn so sánh
    """
    
    documentAText: str = Field(
        ...,
        min_length=10,
        max_length=100000,
        description="Văn bản hợp đồng A (bản gốc/hiện tại)",
        examples=["HỢP ĐỒNG THUÊ NHÀ - Version 1..."],
    )
    documentBText: str = Field(
        ...,
        min_length=10,
        max_length=100000,
        description="Văn bản hợp đồng B (bản sửa đổi/đề xuất)",
        examples=["HỢP ĐỒNG THUÊ NHÀ - Version 2..."],
    )
    contractType: ContractType = Field(
        default=ContractType.UNKNOWN,
        description="Loại hợp đồng. Để UNKNOWN để tự động phân loại.",
    )
    protectedParty: Optional[str] = Field(
        default=None,
        max_length=200,
        description="Bên cần bảo vệ để đánh giá phiên bản nào có lợi hơn",
        examples=["bên thuê", "bên mua"],
    )
    options: AnalysisOptions = Field(default_factory=AnalysisOptions)


class ClauseComparison(BaseModel):
    topic: str
    documentAContent: str = ""
    documentBContent: str = ""
    differenceType: DifferenceType = DifferenceType.SAME
    legalImpact: str = ""
    riskLevel: RiskLevel = RiskLevel.LOW
    recommendation: str = ""


class CompareContractsResponse(BaseModel):
    summary: str
    moreFavorableVersion: FavorableVersion = FavorableVersion.INSUFFICIENT_DATA
    overallRiskLevel: RiskLevel = RiskLevel.MEDIUM
    clauseComparisons: List[ClauseComparison] = Field(default_factory=list)
    riskIncreases: List[str] = Field(default_factory=list)
    riskReductions: List[str] = Field(default_factory=list)
    recommendations: List[str] = Field(default_factory=list)
    llmUsed: bool = False
    fallback: bool = False
