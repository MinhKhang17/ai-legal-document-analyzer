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
    text: str = Field(..., min_length=1)


class ClassifyContractResponse(BaseModel):
    contractType: ContractType = ContractType.UNKNOWN
    confidence: float = 0.0
    reason: str = ""
    detectedParties: List[str] = Field(default_factory=list)
    detectedImportantTerms: List[str] = Field(default_factory=list)


# --- Analyze ------------------------------------------------------------------


class AnalyzeContractRequest(BaseModel):
    contractText: str = Field(..., min_length=1)
    contractType: ContractType = ContractType.UNKNOWN
    protectedParty: Optional[str] = None
    question: Optional[str] = None
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
    documentAText: str = Field(..., min_length=1)
    documentBText: str = Field(..., min_length=1)
    contractType: ContractType = ContractType.UNKNOWN
    protectedParty: Optional[str] = None
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
