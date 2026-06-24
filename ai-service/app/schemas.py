from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


SourceType = Literal["USER_DOCUMENT", "SYSTEM_KB"]


class DocumentProcessRequest(BaseModel):
    jobId: str
    documentId: str
    workspaceId: str
    userId: str
    sourceType: SourceType
    fileName: str
    fileType: str
    filePath: str
    callbackUrl: str


class DocumentProcessAcceptedResponse(BaseModel):
    jobId: str
    documentId: str
    status: Literal["ACCEPTED"] = "ACCEPTED"


class DocumentImportResponse(BaseModel):
    fileName: str
    fileType: str
    filePath: str
    sizeBytes: int


class ExtractedPage(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    page_number: int | None = None
    text: str


class LegalUnit(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    unit_type: Literal["ARTICLE", "CLAUSE", "SECTION", "PARAGRAPH", "UNKNOWN"]
    title: str | None = None
    article_number: str | None = None
    clause_number: str | None = None
    section_title: str | None = None
    page_number: int | None = None
    text: str


class ChunkRecord(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    chunk_text: str
    chunk_index: int
    page_number: int | None = None
    article_number: str | None = None
    clause_number: str | None = None
    section_title: str | None = None
    unit_type: str


class PageDebugInfo(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    page_number: int | None = None
    char_count: int
    text_preview: str = ""


class DocumentProcessResult(BaseModel):
    jobId: str
    status: Literal["READY", "FAILED"]
    chunkCount: int
    pageCount: int
    usedExtractionCache: bool
    fileHash: str
    debugPages: list[PageDebugInfo] = Field(default_factory=list)
    errorMessage: str | None = None


class RagQueryRequest(BaseModel):
    requestId: str
    userId: str
    workspaceId: str
    chatSessionId: str | None = None
    question: str = Field(..., min_length=1)
    topKUserChunks: int = Field(default=5, ge=1, le=20)
    topKKnowledgeChunks: int = Field(default=5, ge=1, le=20)


class RagCitation(BaseModel):
    citationId: str
    sourceType: Literal["USER_DOCUMENT", "SYSTEM_KB"]
    score: float
    documentId: str | None = None
    workspaceId: str | None = None
    userId: str | None = None
    fileName: str | None = None
    knowledgeDocumentId: str | None = None
    lawName: str | None = None
    lawCode: str | None = None
    legalDomain: str | None = None
    pageNumber: int | None = None
    articleNumber: str | None = None
    clauseNumber: str | None = None
    sectionTitle: str | None = None


class RagQueryResponse(BaseModel):
    requestId: str
    chatSessionId: str | None = None
    answer: str
    confidenceScore: float | None = Field(default=None, description="AI confidence used by the UI to decide ticket escalation.")
    shouldSuggestTicket: bool = Field(default=False, description="Whether the frontend should surface a ticket action.")
    suggestionType: Literal["NONE", "ASK_MORE_INFO", "SUGGEST_LAWYER", "REQUIRE_LAWYER"] = Field(
        default="NONE",
        description="Type of legal follow-up the AI recommends.",
    )
    suggestionReason: str | None = Field(default=None, description="Short explanation for the follow-up suggestion.")
    missingInformation: str | None = Field(default=None, description="What information the AI still needs from the user.")
    riskLevel: Literal["LOW", "MEDIUM", "HIGH", "NEED_EXPERT", "UNKNOWN"]
    legalDomain: str | None = Field(default=None, description="Detected legal domain for UI grouping.")
    userActionHint: Literal["CONTINUE_CHAT", "PROVIDE_MORE_INFO", "CREATE_TICKET"] = Field(
        default="CONTINUE_CHAT",
        description="Small UX hint that tells the app how to guide the user next.",
    )
    citations: list[RagCitation] = Field(default_factory=list)
    retrievedUserChunks: int
    retrievedKnowledgeChunks: int


class RagPreviewChunk(BaseModel):
    citationId: str
    sourceType: Literal["USER_DOCUMENT", "SYSTEM_KB"]
    score: float
    chunkText: str
    documentId: str | None = None
    workspaceId: str | None = None
    userId: str | None = None
    fileName: str | None = None
    knowledgeDocumentId: str | None = None
    lawName: str | None = None
    lawCode: str | None = None
    legalDomain: str | None = None
    pageNumber: int | None = None
    articleNumber: str | None = None
    clauseNumber: str | None = None
    sectionTitle: str | None = None


class RagPreviewResponse(BaseModel):
    requestId: str
    chatSessionId: str | None = None
    question: str
    legalSearchQuery: str
    userChunks: list[RagPreviewChunk] = Field(default_factory=list)
    knowledgeChunks: list[RagPreviewChunk] = Field(default_factory=list)
    retrievedUserChunks: int
    retrievedKnowledgeChunks: int
