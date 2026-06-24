from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class ChecklistResult(BaseModel):
    checklist_id: str
    category: str
    title: str
    risk_question: str
    priority: int
    user_chunks_found: list[dict] = Field(default_factory=list)


class RAGQueryRequest(BaseModel):
    request_id: str
    user_id: str
    workspace_id: str
    document_id: str | None = None
    question: str = Field(..., min_length=1)
    top_k_checklist: int = Field(default=10, ge=1, le=50)
    top_k_user_chunks_per_checklist: int = Field(default=3, ge=1, le=20)
    top_k_knowledge_chunks: int = Field(default=5, ge=1, le=20)


class RAGQueryResponse(BaseModel):
    request_id: str
    success: bool
    answer: str | None = None
    confidence_score: float | None = Field(default=None, description="AI confidence used to decide how much the UI should trust the answer.")
    should_suggest_ticket: bool = Field(default=False, description="Signals that the frontend should show a lawyer-ticket action.")
    suggestion_type: Literal["NONE", "ASK_MORE_INFO", "SUGGEST_LAWYER", "REQUIRE_LAWYER"] = Field(
        default="NONE",
        description="What kind of help the AI recommends when the answer is uncertain.",
    )
    suggestion_reason: str | None = Field(default=None, description="Short reason why the AI recommends additional review.")
    missing_information: str | None = Field(default=None, description="Short summary of missing facts the user should provide.")
    checklist_results: list[ChecklistResult] = Field(default_factory=list)
    risk_level: Literal["LOW", "MEDIUM", "HIGH"] = Field(default="LOW", description="High-level risk used by the UI to color the answer.")
    legal_domain: str | None = Field(default=None, description="Detected legal domain for grouping and UI labeling.")
    user_action_hint: Literal["CONTINUE_CHAT", "PROVIDE_MORE_INFO", "CREATE_TICKET"] = Field(
        default="CONTINUE_CHAT",
        description="Simple frontend hint for the next user action.",
    )
    knowledge_chunks: list[dict] = Field(default_factory=list)
    total_checklist_items: int = 0
    total_user_chunks: int = 0
    total_knowledge_chunks: int = 0
    processing_time_ms: float = 0.0
    error_message: str | None = None


class RAGQueryPreviewRequest(BaseModel):
    request_id: str
    user_id: str
    workspace_id: str
    question: str = Field(..., min_length=1)


class RAGQueryStatus(BaseModel):
    request_id: str
    status: Literal["PENDING", "PROCESSING", "READY", "FAILED"]
