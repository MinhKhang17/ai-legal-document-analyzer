"""Chatbot Service with conversation memory and context awareness.

Features:
- Multi-turn conversation
- Session management với in-memory storage
- Contract context awareness
- Smart response generation với Gemini
- Error explanation và suggestions
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

from fastapi import UploadFile

from app.services.legal_rag.contract_error_detector import (
    ContractErrorDetectionService,
    ErrorDetectionReport,
)
from app.services.gemini_client import GeminiClient
from app.core.config import settings

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Data Models
# ---------------------------------------------------------------------------

@dataclass
class ChatMessage:
    role: str  # "user" | "assistant" | "system"
    content: str
    timestamp: datetime = field(default_factory=datetime.now)
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class ContractContext:
    """Context của hợp đồng đang phân tích."""
    contract_id: str
    filename: str
    title: str
    error_report: ErrorDetectionReport
    analyzed_at: datetime = field(default_factory=datetime.now)


@dataclass
class ChatSession:
    """Chat session với history và context."""
    session_id: str
    messages: list[ChatMessage] = field(default_factory=list)
    contract_context: ContractContext | None = None
    created_at: datetime = field(default_factory=datetime.now)
    updated_at: datetime = field(default_factory=datetime.now)


@dataclass
class ChatResponse:
    """Response từ chatbot."""
    session_id: str
    message: str
    sources: list[dict[str, Any]] = field(default_factory=list)
    suggestions: list[str] = field(default_factory=list)


# ---------------------------------------------------------------------------
# Chatbot Service
# ---------------------------------------------------------------------------

class ChatbotService:
    """Chatbot service với conversation memory."""
    
    # In-memory session storage (production nên dùng Redis)
    _sessions: dict[str, ChatSession] = {}
    
    def __init__(self) -> None:
        self.error_detector = ContractErrorDetectionService()
        self.gemini_client = self._build_gemini_client()
    
    async def upload_and_analyze(
        self,
        file: UploadFile,
        session_id: str,
        title: str | None = None,
        initial_question: str | None = None,
    ) -> ChatResponse:
        """Upload hợp đồng, phân tích lỗi và trả lời câu hỏi ban đầu."""
        
        # 1. Analyze contract
        report = await self.error_detector.analyze_upload(file=file, title=title)
        
        # 2. Create/update session
        session = self._get_or_create_session(session_id)
        session.contract_context = ContractContext(
            contract_id=report.document_id,
            filename=report.filename,
            title=report.title,
            error_report=report,
        )
        session.updated_at = datetime.now()
        
        # 3. Build initial summary
        summary = self._build_error_summary(report)
        
        # 4. Answer initial question if provided
        if initial_question and initial_question.strip():
            user_msg = ChatMessage(role="user", content=initial_question)
            session.messages.append(user_msg)
            
            response_text = await self._generate_response(
                session=session,
                user_message=initial_question,
            )
        else:
            response_text = summary
        
        assistant_msg = ChatMessage(role="assistant", content=response_text)
        session.messages.append(assistant_msg)
        
        # 5. Generate suggestions
        suggestions = self._generate_suggestions(report)
        
        return ChatResponse(
            session_id=session_id,
            message=response_text,
            sources=self._extract_error_sources(report),
            suggestions=suggestions,
        )
    
    async def chat(
        self,
        session_id: str,
        message: str,
        contract_id: str | None = None,
    ) -> ChatResponse:
        """Continue conversation."""
        
        session = self._get_or_create_session(session_id)
        
        # Add user message
        user_msg = ChatMessage(role="user", content=message)
        session.messages.append(user_msg)
        session.updated_at = datetime.now()
        
        # Generate response
        response_text = await self._generate_response(
            session=session,
            user_message=message,
        )
        
        # Add assistant message
        assistant_msg = ChatMessage(role="assistant", content=response_text)
        session.messages.append(assistant_msg)
        
        # Extract sources từ context nếu có
        sources = []
        if session.contract_context:
            sources = self._extract_error_sources(session.contract_context.error_report)
        
        return ChatResponse(
            session_id=session_id,
            message=response_text,
            sources=sources,
            suggestions=[],
        )
    
    def clear_session(self, session_id: str) -> None:
        """Xóa session."""
        if session_id in self._sessions:
            del self._sessions[session_id]
    
    def get_history(self, session_id: str) -> list[ChatMessage]:
        """Lấy lịch sử chat."""
        session = self._sessions.get(session_id)
        if not session:
            return []
        return session.messages
    
    # -----------------------------------------------------------------------
    # Private Methods
    # -----------------------------------------------------------------------
    
    def _get_or_create_session(self, session_id: str) -> ChatSession:
        """Lấy hoặc tạo session mới."""
        if session_id not in self._sessions:
            self._sessions[session_id] = ChatSession(session_id=session_id)
        return self._sessions[session_id]
    
    async def _generate_response(
        self,
        session: ChatSession,
        user_message: str,
    ) -> str:
        """Generate response dựa trên context và history."""
        
        if not self.gemini_client:
            return self._generate_fallback_response(session, user_message)
        
        # Build context cho Gemini
        system_prompt = self._build_system_prompt(session)
        conversation_context = self._build_conversation_context(session)
        
        user_prompt = f"{conversation_context}\n\nUser: {user_message}\nAssistant:"
        
        # Call Gemini
        result = self.gemini_client.generate_text(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
        )
        
        if result.error or not result.text:
            logger.warning("Gemini error: %s", result.error)
            return self._generate_fallback_response(session, user_message)
        
        return result.text.strip()
    
    def _build_system_prompt(self, session: ChatSession) -> str:
        """Build system prompt cho Gemini."""
        
        base_prompt = """Bạn là chuyên gia pháp lý Việt Nam chuyên tư vấn hợp đồng.

Nhiệm vụ:
- Trả lời câu hỏi về lỗi sai trong hợp đồng
- Giải thích chi tiết các lỗi đã phát hiện
- Đưa ra gợi ý sửa chữa cụ thể
- Trích dẫn căn cứ pháp lý (BLDS 2015, Luật Nhà ở 2023)

Phong cách:
- Chuyên nghiệp nhưng dễ hiểu
- Ngắn gọn, súc tích (tối đa 300 từ)
- Ưu tiên thông tin thực tế từ phân tích"""
        
        # Thêm contract context nếu có
        if session.contract_context:
            report = session.contract_context.error_report
            context_info = f"""

Thông tin hợp đồng đang phân tích:
- Tên file: {report.filename}
- Tiêu đề: {report.title}
- Tổng số lỗi: {report.summary.total_errors}
  + Thiếu điều khoản: {report.summary.missing_clause_count}
  + Lỗi hình thức: {report.summary.format_error_count}
  + Lỗi logic: {report.summary.logical_error_count}
  + Rủi ro pháp lý: {report.summary.legal_risk_count}
- Độ nghiêm trọng: HIGH={report.summary.high_count}, MEDIUM={report.summary.medium_count}, LOW={report.summary.low_count}

Danh sách lỗi:
{self._format_errors_for_context(report.errors[:10])}
"""
            base_prompt += context_info
        
        return base_prompt
    
    def _build_conversation_context(self, session: ChatSession) -> str:
        """Build conversation history (3 turns gần nhất)."""
        recent_messages = session.messages[-6:]  # 3 turns = 6 messages
        
        if not recent_messages:
            return ""
        
        context_lines = ["Lịch sử hội thoại:"]
        for msg in recent_messages:
            role = "User" if msg.role == "user" else "Assistant"
            context_lines.append(f"{role}: {msg.content}")
        
        return "\n".join(context_lines)
    
    def _generate_fallback_response(
        self,
        session: ChatSession,
        user_message: str,
    ) -> str:
        """Fallback response khi không có Gemini."""
        
        if not session.contract_context:
            return "Vui lòng upload hợp đồng trước để tôi có thể phân tích và trả lời câu hỏi của bạn."
        
        report = session.contract_context.error_report
        
        # Detect intent từ keywords
        message_lower = user_message.lower()
        
        if any(kw in message_lower for kw in ["tổng quan", "tóm tắt", "summary", "overview"]):
            return self._build_error_summary(report)
        
        if any(kw in message_lower for kw in ["nghiêm trọng", "high", "quan trọng"]):
            high_errors = [e for e in report.errors if e.severity == "HIGH"]
            if not high_errors:
                return "Hợp đồng không có lỗi nghiêm trọng (HIGH)."
            return self._format_errors_list(high_errors, "Các lỗi nghiêm trọng:")
        
        if "format_" in message_lower or "missing_" in message_lower or "logic_" in message_lower or "risk_" in message_lower:
            # User hỏi về error ID cụ thể
            error_id = self._extract_error_id(user_message)
            if error_id:
                error = next((e for e in report.errors if e.error_id == error_id), None)
                if error:
                    return self._format_single_error(error)
        
        # Default: return summary
        return self._build_error_summary(report)
    
    def _build_error_summary(self, report: ErrorDetectionReport) -> str:
        """Build error summary."""
        
        lines = [
            f"📋 **Phân tích hợp đồng: {report.title}**\n",
            f"**Tổng số lỗi:** {report.summary.total_errors}",
            f"- 🔴 Nghiêm trọng (HIGH): {report.summary.high_count}",
            f"- 🟡 Trung bình (MEDIUM): {report.summary.medium_count}",
            f"- 🟢 Thấp (LOW): {report.summary.low_count}\n",
            
            f"**Phân loại:**",
            f"- Thiếu điều khoản bắt buộc: {report.summary.missing_clause_count}",
            f"- Lỗi hình thức: {report.summary.format_error_count}",
            f"- Lỗi logic/mâu thuẫn: {report.summary.logical_error_count}",
            f"- Rủi ro pháp lý: {report.summary.legal_risk_count}",
        ]
        
        if report.summary.total_errors > 0:
            lines.append("\n**Top 3 lỗi nghiêm trọng nhất:**")
            top_errors = sorted(report.errors, key=lambda e: (
                {"HIGH": 0, "MEDIUM": 1, "LOW": 2}.get(e.severity, 3),
                -e.confidence,
            ))[:3]
            
            for idx, error in enumerate(top_errors, 1):
                lines.append(f"{idx}. [{error.severity}] {error.title}")
        
        return "\n".join(lines)
    
    def _format_errors_for_context(self, errors: list) -> str:
        """Format errors cho system prompt."""
        if not errors:
            return "(Không có lỗi)"
        
        lines = []
        for error in errors:
            lines.append(f"- [{error.error_id}] {error.title} ({error.severity})")
        return "\n".join(lines)
    
    def _format_errors_list(self, errors: list, title: str) -> str:
        """Format danh sách lỗi."""
        if not errors:
            return f"{title}\n(Không có lỗi)"
        
        lines = [f"**{title}**\n"]
        for idx, error in enumerate(errors, 1):
            lines.append(f"{idx}. **{error.title}** ({error.error_id})")
            lines.append(f"   {error.description[:150]}...")
            if error.suggestion:
                lines.append(f"   💡 {error.suggestion[:100]}...")
            lines.append("")
        
        return "\n".join(lines)
    
    def _format_single_error(self, error) -> str:
        """Format chi tiết 1 lỗi."""
        lines = [
            f"**{error.title}**",
            f"ID: {error.error_id}",
            f"Loại: {error.category}",
            f"Mức độ: {error.severity}",
            f"Độ tin cậy: {error.confidence:.0%}\n",
            f"**Mô tả:**",
            error.description,
        ]
        
        if error.suggestion:
            lines.append(f"\n**Gợi ý sửa:**")
            lines.append(error.suggestion)
        
        if error.legal_basis:
            lines.append(f"\n**Căn cứ pháp lý:**")
            lines.append(error.legal_basis)
        
        if error.clause_reference:
            lines.append(f"\n**Liên quan:**")
            lines.append(error.clause_reference)
        
        return "\n".join(lines)
    
    def _extract_error_id(self, message: str) -> str | None:
        """Extract error ID từ message."""
        import re
        match = re.search(r"(MISSING|FORMAT|LOGIC|RISK)_\d{3}", message, re.IGNORECASE)
        if match:
            return match.group(0).upper()
        return None
    
    def _generate_suggestions(self, report: ErrorDetectionReport) -> list[str]:
        """Generate suggestion questions."""
        suggestions = []
        
        if report.summary.high_count > 0:
            suggestions.append("Các lỗi nghiêm trọng (HIGH) cần sửa ngay là gì?")
        
        if report.summary.missing_clause_count > 0:
            suggestions.append("Những điều khoản bắt buộc nào đang bị thiếu?")
        
        if report.summary.format_error_count > 0:
            suggestions.append("Làm sao để sửa các lỗi hình thức?")
        
        if report.summary.logical_error_count > 0:
            suggestions.append("Giải thích các lỗi logic/mâu thuẫn")
        
        suggestions.append("So sánh với mẫu hợp đồng chuẩn")
        
        return suggestions[:4]
    
    def _extract_error_sources(self, report: ErrorDetectionReport) -> list[dict[str, Any]]:
        """Extract error sources để hiển thị."""
        sources = []
        
        for error in report.errors[:5]:  # Top 5 errors
            sources.append({
                "error_id": error.error_id,
                "title": error.title,
                "severity": error.severity,
                "category": error.category,
            })
        
        return sources
    
    def _build_gemini_client(self) -> GeminiClient | None:
        """Build Gemini client."""
        if not settings.gemini_api_key or not settings.gemini_model:
            logger.warning("Gemini not configured, chatbot will use fallback responses")
            return None
        
        return GeminiClient(
            api_key=settings.gemini_api_key,
            model=settings.gemini_model,
            base_url=settings.gemini_base_url,
            timeout_seconds=max(settings.gemini_timeout_seconds, 60.0),
            max_output_tokens=max(settings.gemini_max_output_tokens, 2048),
        )
