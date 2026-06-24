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
    requestId: str = Field(alias="request_id")
    userId: str = Field(alias="user_id")
    workspaceId: str = Field(alias="workspace_id")
    documentId: str | None = Field(default=None, alias="document_id")
    chatSessionId: str | None = Field(default=None, alias="chat_session_id")
    chatHistory: str | None = Field(default=None, alias="chat_history")
    question: str = Field(..., min_length=1)
    topKUserChunks: int = Field(default=5, ge=1, le=20, alias="top_k_user_chunks")
    topKKnowledgeChunks: int = Field(default=5, ge=1, le=20, alias="top_k_knowledge_chunks")
    topKChecklist: int = Field(default=10, ge=1, le=50, alias="top_k_checklist")
    topKUserChunksPerChecklist: int = Field(default=3, ge=1, le=10, alias="top_k_user_chunks_per_checklist")
    
    model_config = {
        "populate_by_name": True
    }


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
    riskLevel: Literal["LOW", "MEDIUM", "HIGH", "NEED_EXPERT", "UNKNOWN"]
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
