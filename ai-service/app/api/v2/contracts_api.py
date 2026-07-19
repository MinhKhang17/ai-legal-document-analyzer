from __future__ import annotations

from functools import lru_cache
import json
from typing import Any

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from pydantic import BaseModel, ConfigDict, Field
from app.models.intent_enums import ContractType

from app.services.legal_rag.pipeline import (
    ClauseFinding,
    ContractAnalysisReport,
    ContractAnalysisService,
    LegalBasis,
)
from app.services.llm_client import build_default_llm_client
from app.services.retrieval_service import RetrievalService


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
    contract_type: str | None = None


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
    contract_type: str | None = Form(default=None),
) -> ContractAnalysisResponse:
    supported = {
        ContractType.RENTAL, ContractType.PART_TIME_EMPLOYMENT, ContractType.INTERNSHIP,
        ContractType.COLLABORATOR, ContractType.FREELANCE_SERVICE,
        ContractType.SMALL_ASSET_SALE, ContractType.PERSONAL_LOAN,
    }
    try:
        selected_type = ContractType(contract_type) if contract_type else ContractType.UNKNOWN
    except ValueError as exception:
        raise HTTPException(status_code=422, detail="UNSUPPORTED_CONTRACT_TYPE") from exception
    if selected_type not in supported:
        raise HTTPException(
            status_code=422,
            detail={
                "code": "CONTRACT_TYPE_CONFIRMATION_REQUIRED" if not contract_type else "UNSUPPORTED_CONTRACT_TYPE",
                "supportedContractTypes": sorted(item.value for item in supported),
            },
        )
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
        contract_type=selected_type.value,
    )


class GenerateContractApiRequest(BaseModel):
    requestId: str
    templateContent: str | None = None
    inputJson: str
    contractType: str


class GenerateContractApiResponse(BaseModel):
    requestId: str
    promptSnapshot: str
    outputDraft: str
    error: str | None = None


@router.post("/generate", response_model=GenerateContractApiResponse)
def generate_contract(payload: GenerateContractApiRequest) -> GenerateContractApiResponse:
    supported = {
        ContractType.RENTAL, ContractType.PART_TIME_EMPLOYMENT, ContractType.INTERNSHIP,
        ContractType.COLLABORATOR, ContractType.FREELANCE_SERVICE,
        ContractType.SMALL_ASSET_SALE, ContractType.PERSONAL_LOAN,
    }
    try:
        selected_type = ContractType(payload.contractType)
    except ValueError as exception:
        raise HTTPException(status_code=422, detail="UNSUPPORTED_CONTRACT_TYPE") from exception
    if selected_type not in supported:
        raise HTTPException(status_code=422, detail="UNSUPPORTED_CONTRACT_TYPE")
    try:
        inputs = json.loads(payload.inputJson)
    except Exception as e:
        return GenerateContractApiResponse(
            requestId=payload.requestId,
            promptSnapshot="",
            outputDraft="",
            error=f"Invalid inputJson: {str(e)}"
        )

    retrieval_service = RetrievalService()
    retrieved_chunks = retrieval_service.search_knowledge_chunks("residential lease agreement", top_k=3)

    documents_text = ""
    if retrieved_chunks:
        for idx, chunk in enumerate(retrieved_chunks, start=1):
            documents_text += f"Reference Document {idx}:\n{chunk.chunkText}\n\n"
    else:
        documents_text = "[No reference documents found in knowledge base]"

    template = payload.templateContent
    uses_default_template = not template or not template.strip()
    if not template or not template.strip():
        template = """# ROLE

You are an AI drafting assistant limited to simple personal residential rental agreements.
You do not replace a lawyer and must not guarantee legality, compliance, or accuracy.

Your responsibility is to generate a brand-new residential lease agreement based on legal reference documents retrieved from a knowledge base.

-----------------------------------------------------

# CONTEXT

The reference agreements below were retrieved from a legal knowledge base using semantic search.

These documents are examples only.

You must use them as legal guidance.

Never copy personal information from them.

-----------------------------------------------------

# REFERENCE DOCUMENTS

{documents}

-----------------------------------------------------

# USER REQUIREMENTS

Landlord:
{landlord}

Tenant:
{tenant}

Property Address:
{address}

Property Type:
{propertyType}

Lease Start Date:
{startDate}

Lease End Date:
{endDate}

Monthly Rent:
{rent}

Security Deposit:
{deposit}

Payment Method:
{paymentMethod}

Utilities:
{utilities}

Pets:
{pets}

Parking:
{parking}

Special Conditions:
{specialConditions}

-----------------------------------------------------

# YOUR TASK

Carefully analyze all reference agreements.

Identify the common legal structure.

Identify mandatory clauses.

Identify optional clauses.

Merge the best practices found in the references.

Generate a completely NEW residential lease agreement.

Never copy names, addresses, signatures, identification numbers, emails, phone numbers or other personal information from the reference agreements.

Rewrite every clause using your own wording.

Maintain professional legal English.

Use a logical structure.

Use numbered headings.

If some user information is missing, insert placeholders enclosed in square brackets.

-----------------------------------------------------

# REQUIRED CLAUSES

Your generated agreement should include:

1. Parties

2. Property Description

3. Lease Term

4. Rent

5. Security Deposit

6. Payment Terms

7. Utilities

8. Maintenance Responsibilities

9. Repairs

10. Tenant Obligations

11. Landlord Obligations

12. Pets

13. Smoking

14. Visitors

15. Alterations

16. Entry Rights

17. Insurance

18. Early Termination

19. Breach of Contract

20. Governing Law

21. Entire Agreement

22. Signature Section

-----------------------------------------------------

# OUTPUT RULES

Return only the lease agreement.

Do not explain anything.

Do not mention the reference documents.

Do not use Markdown.

Generate a document that is ready for export as DOCX or PDF."""

    if uses_default_template and selected_type != ContractType.RENTAL:
        template = """Draft a simple Vietnamese personal contract of confirmed type: {contractType}.
Use only the user requirements below. Include parties, subject, obligations, payment (if applicable), term, termination, dispute handling, and signatures.
Do not invent missing personal data; use square-bracket placeholders. Do not claim guaranteed legality or compliance.

USER REQUIREMENTS:
{requirements}

Return only the contract text."""

    placeholders = {
        "documents": documents_text,
        "contractType": selected_type.value,
        "requirements": json.dumps(inputs, ensure_ascii=False),
        "landlord": inputs.get("landlord", "[Landlord]"),
        "tenant": inputs.get("tenant", "[Tenant]"),
        "address": inputs.get("address", "[Property Address]"),
        "propertyType": inputs.get("propertyType", "[Property Type]"),
        "startDate": inputs.get("startDate", "[Lease Start Date]"),
        "endDate": inputs.get("endDate", "[Lease End Date]"),
        "rent": inputs.get("rent", "[Monthly Rent]"),
        "deposit": inputs.get("deposit", "[Security Deposit]"),
        "paymentMethod": inputs.get("paymentMethod", "[Payment Method]"),
        "utilities": inputs.get("utilities", "[Utilities]"),
        "pets": inputs.get("pets", "[Pets]"),
        "parking": inputs.get("parking", "[Parking]"),
        "specialConditions": inputs.get("specialConditions", "[Special Conditions]")
    }

    user_prompt = template
    for key, value in placeholders.items():
        user_prompt = user_prompt.replace(f"{{{key}}}", str(value))

    system_prompt = (
        "You are an AI drafting assistant for simple personal contracts only. "
        f"The user confirmed contract type {selected_type.value}. "
        "Do not act as a lawyer or guarantee legality, compliance, or accuracy."
    )

    llm_client = build_default_llm_client()
    try:
        llm_result = llm_client.generate(system_prompt=system_prompt, user_prompt=user_prompt)
        if llm_result.error:
            return GenerateContractApiResponse(
                requestId=payload.requestId,
                promptSnapshot=user_prompt,
                outputDraft="",
                error=llm_result.error
            )
        draft = llm_result.answer or ""
        return GenerateContractApiResponse(
            requestId=payload.requestId,
            promptSnapshot=user_prompt,
            outputDraft=draft,
            error=None
        )
    except Exception as e:
        return GenerateContractApiResponse(
            requestId=payload.requestId,
            promptSnapshot=user_prompt,
            outputDraft="",
            error=f"LLM generation failed: {str(e)}"
        )
