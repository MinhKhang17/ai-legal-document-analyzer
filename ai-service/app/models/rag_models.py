"""Pydantic models for the RAG API (context retrieval)."""
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class RagFilters(BaseModel):
    riskTypes: List[str] = Field(default_factory=list)
    clauseTypes: List[str] = Field(default_factory=list)


class RagRetrieveRequest(BaseModel):
    query: str = Field(..., min_length=1)
    contractType: Optional[str] = None
    topK: int = Field(default=8, ge=1, le=50)
    filters: RagFilters = Field(default_factory=RagFilters)


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
    contractType: str = Field(..., min_length=1)
    question: Optional[str] = None
    detectedClauseTypes: List[str] = Field(default_factory=list)


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
