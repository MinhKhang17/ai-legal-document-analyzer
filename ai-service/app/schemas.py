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
    contractType: str | None = None
    contractTypeConfirmed: bool | None = None


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


class ConversationMessage(BaseModel):
    messageId: str
    role: str
    content: str
    createdAt: str | None = None
    documentIds: list[str] = Field(default_factory=list)
    citationIds: list[str] = Field(default_factory=list)


class RagQueryRequest(BaseModel):
    requestId: str = Field(alias="request_id")
    userId: str = Field(alias="user_id")
    workspaceId: str = Field(alias="workspace_id")
    documentId: str | None = Field(default=None, alias="document_id")
    attachedDocumentIds: list[str] | None = Field(default=None, alias="attached_document_ids")
    chatSessionId: str | None = Field(default=None, alias="chat_session_id")
    chatHistory: str | None = Field(default=None, alias="chat_history")
    conversationSummaryJson: str | None = Field(default=None, alias="conversation_summary_json")
    recentHistory: list[ConversationMessage] = Field(default_factory=list, alias="recent_history")
    evictedMessages: list[ConversationMessage] = Field(default_factory=list, alias="evicted_messages")
    currentUserMessageId: str | None = Field(default=None, alias="current_user_message_id")
    currentAssistantMessageId: str | None = Field(default=None, alias="current_assistant_message_id")
    focusedDocumentId: str | None = Field(default=None, alias="focused_document_id")
    messageAttachedDocumentIds: list[str] = Field(default_factory=list, alias="message_attached_document_ids")
    conversationUserRole: str | None = Field(default=None, alias="conversation_user_role")
    conversationMode: str | None = Field(default=None, alias="conversation_mode")
    draftingAction: str | None = Field(default=None, alias="drafting_action")
    draftingContractType: str | None = Field(default=None, alias="drafting_contract_type")
    draftingInformation: dict[str, str | None] = Field(default_factory=dict, alias="drafting_information")
    draftingOriginalRequirement: str | None = Field(default=None, alias="drafting_original_requirement")
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
    excerpt: str | None = None


class RagUsage(BaseModel):
    promptTokens: int = 0
    completionTokens: int = 0
    totalTokens: int = 0


class ConversationMemoryUpdate(BaseModel):
    summaryJson: str | None = None
    summarizedThroughMessageId: str | None = None
    updated: bool = False


class TokenUsageBreakdown(BaseModel):
    systemPrompt: int = 0
    conversationSummary: int = 0
    recentHistory: int = 0
    relevantHistory: int = 0
    userDocumentContext: int = 0
    legalKbContext: int = 0
    output: int = 0


class KeyClause(BaseModel):
    """A key clause identified in the contract."""
    name: str = Field(..., description="Name of the clause.")
    content: str | None = Field(default=None, description="Brief content summary.")
    assessment: str | None = Field(default=None, description="Assessment of the clause quality.")


class MissingClauseItem(BaseModel):
    """A clause that is missing from the contract."""
    name: str = Field(..., description="Name of the missing clause.")
    importance: Literal["LOW", "MEDIUM", "HIGH"] = Field(
        default="MEDIUM",
        description="How important this missing clause is.",
    )
    reason: str = Field(default="", description="Why this clause is important.")
    suggestedContent: str | None = Field(default=None, description="Suggested content for the missing clause.")


class RiskItem(BaseModel):
    """A risk item identified in the contract."""
    title: str = Field(..., description="Short title of the risk.")
    riskLevel: Literal["LOW", "MEDIUM", "HIGH", "CRITICAL"] = Field(
        default="MEDIUM",
        description="Severity of the risk.",
    )
    description: str = Field(default="", description="Detailed description of the risk.")
    clause: str | None = Field(default=None, description="Which clause this risk relates to.")
    recommendation: str | None = Field(default=None, description="How to mitigate this risk.")


class AnalysisResult(BaseModel):
    """Structured analysis output from the AI."""
    summary: str | None = Field(default=None, description="Overall summary of the analysis.")
    keyClauses: list[KeyClause] = Field(default_factory=list, description="Key clauses identified.")
    missingClauses: list[MissingClauseItem] = Field(default_factory=list, description="Missing clauses.")
    riskItems: list[RiskItem] = Field(default_factory=list, description="Risk items found.")
    recommendations: list[str] = Field(default_factory=list, description="Action recommendations.")
    questionsToUser: list[str] = Field(default_factory=list, description="Follow-up questions for the user.")


class DraftingQuestion(BaseModel):
    key: str
    label: str
    placeholder: str | None = None
    required: bool = False


class RagQueryResponse(BaseModel):
    requestId: str
    chatSessionId: str | None = None
    answer: str
    confidenceScore: float | None = Field(default=None, description="AI confidence used by the UI to decide ticket escalation.")
    shouldSuggestTicket: bool = Field(default=False, description="Whether the frontend should surface a ticket action.")
    suggestionType: Literal[
        "NONE",
        "ASK_MORE_INFO",
        "SUGGEST_LAWYER",
        "REQUIRE_LAWYER",
        "DIRECT_ANSWER",
        "ASK_UPLOAD_CONTRACT",
        "ASK_CONTRACT_TYPE",
        "ASK_USER_ROLE",
        "ASK_TARGET_CLAUSE",
        "ASK_MORE_FACTS",
        "SUGGEST_REVISE_CLAUSE",
        "SUGGEST_NEGOTIATION",
        "REDIRECT_TO_SUPPORTED_SCOPE",
        "REFUSE_AND_REDIRECT",
    ] = Field(
        default="NONE",
        description="Type of legal follow-up the AI recommends.",
    )
    suggestionReason: str | None = Field(default=None, description="Short explanation for the follow-up suggestion.")
    missingInformation: str | None = Field(default=None, description="What information the AI still needs from the user.")
    riskLevel: Literal["NONE", "LOW", "MEDIUM", "HIGH", "CRITICAL", "UNKNOWN"]
    legalDomain: str | None = Field(default=None, description="Detected legal domain for UI grouping.")
    userActionHint: Literal["CONTINUE_CHAT", "PROVIDE_MORE_INFO", "CREATE_TICKET", "UPLOAD_CONTRACT", "CONTACT_LAWYER"] = Field(
        default="CONTINUE_CHAT",
        description="Small UX hint that tells the app how to guide the user next.",
    )
    citations: list[RagCitation] = Field(default_factory=list)
    usedKnowledgeCitationIds: list[str] = Field(
        default_factory=list,
        description="Validated SYSTEM_KB citations actually used by the answer.",
    )
    usedUserCitationIds: list[str] = Field(
        default_factory=list,
        description="Validated user-document citations actually used by the answer.",
    )
    retrievedUserChunks: int
    retrievedKnowledgeChunks: int

    # ── NEW fields (all optional for backward compatibility) ──
    intent: str | None = Field(default=None, description="Detected LegalQueryIntent (e.g. FULL_CONTRACT_REVIEW).")
    intents: list[str] = Field(default_factory=list, description="Ordered detected intents.")
    contractType: str | None = Field(default=None, description="Detected ContractType (e.g. RENTAL, LABOR).")
    userRole: str | None = Field(default=None, description="Detected role of the user in the contract.")
    jurisdiction: str | None = Field(default=None, description="Detected jurisdiction for the query.")
    responseStatus: str | None = Field(default=None, description="Classifier decision on answerability.")
    responseMode: str | None = Field(default=None, description="ResponseMode used (e.g. DOCUMENT_BASED_ANALYSIS).")
    inputComplete: bool | None = Field(default=None, description="Whether the user provided all required input.")
    missingInputs: list[str] | None = Field(default=None, description="List of missing input items.")
    analysis: AnalysisResult | None = Field(default=None, description="Structured analysis output.")
    suggestedActions: list[str] = Field(default_factory=list)
    selectedDocumentIds: list[str] = Field(default_factory=list)
    draftingPrompt: str | None = None
    redactionRequired: bool = False
    draftingStatus: str | None = None
    questions: list[DraftingQuestion] = Field(default_factory=list)
    providedInformation: dict[str, str] = Field(default_factory=dict)
    draftingMissingInformation: list[str] = Field(default_factory=list)
    privacyWarning: str | None = None
    draftingOriginalRequirement: str | None = None
    model: str | None = None
    usage: RagUsage | None = None
    llmExecuted: bool | None = Field(default=None, description="True only when the prompt was sent to the configured LLM provider.")
    systemPromptPreview: str | None = Field(default=None, description="Final system prompt in LLM preview mode.")
    userPromptPreview: str | None = Field(default=None, description="Final user prompt in LLM preview mode.")
    conversationMemoryUpdate: ConversationMemoryUpdate | None = None
    tokenUsageBreakdown: TokenUsageBreakdown | None = None




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
