from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class ReviewChecklistItem(BaseModel):
    checklist_id: str
    source_type: str = "REVIEW_CHECKLIST"
    document_type: str = "ANY"
    checklist_layer: str = "UNIVERSAL"
    category: str
    title: str
    query_text: str
    risk_question: str
    priority: int
    version: str = "v1"
    is_active: bool = True
    embedding: list[float] | None = Field(default=None)
    metadata: dict[str, Any] = Field(default_factory=dict)
