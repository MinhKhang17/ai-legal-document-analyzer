"""Pydantic models for the RAG API (context retrieval)."""
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class RagFilters(BaseModel):
    riskTypes: List[str] = Field(default_factory=list)
    clauseTypes: List[str] = Field(default_factory=list)


class RagRetrieveRequest(BaseModel):
    """Request để truy xuất context từ knowledge graph.
    
    Attributes:
        query: Câu query để search
        contractType: Loại hợp đồng để filter (tùy chọn)
        topK: Số lượng items trả về (1-50)
        filters: Filters bổ sung theo risk types và clause types
    """
    
    query: str = Field(
        ...,
        min_length=2,
        max_length=500,
        description="Câu query để tìm kiếm context pháp lý",
        examples=["rủi ro đặt cọc", "điều khoản thanh toán trong hợp đồng thuê nhà"],
    )
    contractType: Optional[str] = Field(
        default=None,
        max_length=50,
        description="Loại hợp đồng để filter results",
        examples=["HOUSE_RENTAL", "LAND_TRANSFER"],
    )
    topK: int = Field(
        default=8,
        ge=1,
        le=50,
        description="Số lượng context items trả về (1-50)",
    )
    filters: RagFilters = Field(
        default_factory=RagFilters,
        description="Filters bổ sung theo risk types và clause types",
    )


class RagContextItem(BaseModel):
    id: str
    type: str = Field(..., description="RiskType | ClauseType | LegalConcept | Recommendation | ContractType")
    title: str
    content: str
    score: float = 0.0
    metadata: Dict[str, Any] = Field(default_factory=dict)


class RagRetrieveResponse(BaseModel):
    query: str
    items: List[RagContextItem] = Field(default_factory=list)


class ContractContextRequest(BaseModel):
    """Request để build context cho một loại hợp đồng.
    
    Attributes:
        contractType: Loại hợp đồng
        question: Câu hỏi cụ thể (tùy chọn)
        detectedClauseTypes: Các điều khoản đã phát hiện trong hợp đồng
    """
    
    contractType: str = Field(
        ...,
        min_length=1,
        max_length=50,
        description="Loại hợp đồng cần build context",
        examples=["HOUSE_RENTAL", "LAND_TRANSFER", "SERVICE_CONTRACT"],
    )
    question: Optional[str] = Field(
        default=None,
        max_length=500,
        description="Câu hỏi cụ thể về loại hợp đồng này (tùy chọn)",
        examples=["Cần chú ý điều gì khi thuê nhà?"],
    )
    detectedClauseTypes: List[str] = Field(
        default_factory=list,
        description="Các loại điều khoản đã phát hiện trong hợp đồng",
        examples=[["PAYMENT", "DEPOSIT", "PARTY_INFO"]],
    )


class RequiredCheck(BaseModel):
    clauseType: str
    title: str
    present: bool
    description: str = ""


class ContractContextResponse(BaseModel):
    contractType: str
    requiredChecks: List[RequiredCheck] = Field(default_factory=list)
    relatedRisks: List[RagContextItem] = Field(default_factory=list)
    recommendations: List[RagContextItem] = Field(default_factory=list)
