"""Chatbot API for interactive contract error checking and consultation.

Supports:
- Multi-turn conversation với context
- Upload file → Analyze → Ask follow-up questions
- Explain errors, suggest fixes
- Legal advice based on BLDS 2015, Luật Nhà ở 2023
"""
from __future__ import annotations

import json
import logging
from typing import Any
from functools import lru_cache

from fastapi import APIRouter, UploadFile, File, Form
from pydantic import BaseModel, Field

from app.services.legal_rag.chatbot_service import (
    ChatbotService,
    ChatMessage,
    ChatResponse,
    ContractContext,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/v2/chatbot", tags=["chatbot"])


# ---------------------------------------------------------------------------
# Request/Response Models
# ---------------------------------------------------------------------------

class ChatRequest(BaseModel):
    """User message to chatbot."""
    session_id: str = Field(..., description="Unique session ID to maintain context")
    message: str = Field(..., description="User's question or message")
    contract_id: str | None = Field(default=None, description="Contract ID if discussing specific contract")


class ChatResponseModel(BaseModel):
    """Chatbot response."""
    session_id: str
    message: str
    sources: list[dict[str, Any]] = Field(default_factory=list)
    suggestions: list[str] = Field(default_factory=list)
    context_updated: bool = False


class UploadAndChatRequest(BaseModel):
    """Upload contract and start conversation."""
    session_id: str
    initial_question: str | None = None


# ---------------------------------------------------------------------------
# Service Singleton
# ---------------------------------------------------------------------------

@lru_cache(maxsize=1)
def get_chatbot_service() -> ChatbotService:
    return ChatbotService()


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@router.post("/upload", response_model=ChatResponseModel)
async def upload_contract_and_chat(
    file: UploadFile = File(...),
    session_id: str = Form(...),
    initial_question: str = Form(default="Hãy phân tích hợp đồng này và cho tôi biết có lỗi gì không?"),
    title: str | None = Form(default=None),
) -> ChatResponseModel:
    """Upload hợp đồng và bắt đầu chat.
    
    Chatbot sẽ:
    1. Phân tích hợp đồng tìm lỗi
    2. Lưu context vào session
    3. Trả lời câu hỏi ban đầu
    """
    service = get_chatbot_service()
    
    # Analyze contract
    response = await service.upload_and_analyze(
        file=file,
        session_id=session_id,
        title=title,
        initial_question=initial_question,
    )
    
    return ChatResponseModel(
        session_id=response.session_id,
        message=response.message,
        sources=response.sources,
        suggestions=response.suggestions,
        context_updated=True,
    )


@router.post("/chat", response_model=ChatResponseModel)
async def chat(payload: ChatRequest) -> ChatResponseModel:
    """Continue conversation với chatbot.
    
    Examples:
    - "Giải thích chi tiết lỗi FORMAT_001"
    - "Làm sao để sửa lỗi thiếu ngày ký?"
    - "Điều khoản nào vi phạm BLDS 2015?"
    - "So sánh với mẫu hợp đồng chuẩn"
    """
    service = get_chatbot_service()
    
    response = await service.chat(
        session_id=payload.session_id,
        message=payload.message,
        contract_id=payload.contract_id,
    )
    
    return ChatResponseModel(
        session_id=response.session_id,
        message=response.message,
        sources=response.sources,
        suggestions=response.suggestions,
        context_updated=False,
    )


@router.post("/clear-session")
async def clear_session(session_id: str) -> dict[str, str]:
    """Xóa session history và context."""
    service = get_chatbot_service()
    service.clear_session(session_id)
    return {"status": "ok", "message": f"Session {session_id} cleared"}


@router.get("/sessions/{session_id}/history")
async def get_history(session_id: str) -> list[dict[str, Any]]:
    """Lấy lịch sử chat của session."""
    service = get_chatbot_service()
    history = service.get_history(session_id)
    return [
        {
            "role": msg.role,
            "content": msg.content,
            "timestamp": msg.timestamp.isoformat() if msg.timestamp else None,
        }
        for msg in history
    ]
