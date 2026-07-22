from __future__ import annotations

import re
import unicodedata

from app.schemas import RagQueryRequest, RagQueryResponse, RagUsage
from app.services.drafting_workflow import build_drafting_response, redact_sensitive_text


def _plain(value: str) -> str:
    return "".join(
        char for char in unicodedata.normalize("NFD", value.lower())
        if unicodedata.category(char) != "Mn"
    )


def is_capability_question(question: str) -> bool:
    value = _plain(question)
    phrases = (
        "ban lam duoc gi", "he thong ho tro gi", "chuc nang gi", "co the giup gi",
        "what can you do", "what do you support", "your capabilities",
    )
    return any(phrase in value for phrase in phrases)


def is_greeting(question: str) -> bool:
    value = _plain(question).strip(" .!?\t\r\n")
    return value in {"xin chao", "chao", "hello", "hi", "hey"}


def is_thank_you(question: str) -> bool:
    value = _plain(question).strip(" .!?\t\r\n")
    return value in {"cam on", "cam on ban", "thanks", "thank you", "thank you so much"}


def _base_response(request: RagQueryRequest, *, answer: str, intent: str, actions: list[str] | None = None,
                   drafting_prompt: str | None = None, redaction_required: bool = False) -> RagQueryResponse:
    return RagQueryResponse(
        requestId=request.requestId,
        chatSessionId=request.chatSessionId,
        answer=answer,
        confidenceScore=1.0,
        shouldSuggestTicket=False,
        suggestionType="DIRECT_ANSWER",
        riskLevel="NONE",
        legalDomain=None,
        userActionHint="CONTINUE_CHAT",
        citations=[],
        usedKnowledgeCitationIds=[],
        usedUserCitationIds=[],
        retrievedUserChunks=0,
        retrievedKnowledgeChunks=0,
        intent=intent,
        intents=[intent],
        responseStatus="ANSWERABLE",
        responseMode="DIRECT_ANSWER",
        inputComplete=True,
        suggestedActions=actions or [],
        selectedDocumentIds=[],
        draftingPrompt=drafting_prompt,
        redactionRequired=redaction_required,
        model="deterministic-policy-router",
        usage=RagUsage(),
        llmExecuted=False,
    )


def build_conversation_shortcut(request: RagQueryRequest) -> RagQueryResponse | None:
    if is_capability_question(request.question):
        answer = (
            "Tôi hỗ trợ giải thích câu hỏi pháp lý phổ thông ngay cả khi bạn chưa đính kèm tài liệu; "
            "tóm tắt, trích xuất thông tin, đối chiếu nhiều tài liệu, phân tích điều khoản và rủi ro; "
            "gợi ý sửa điều khoản, phương án thương lượng và các điểm cần cân nhắc trước khi ký. "
            "Nếu bạn yêu cầu soạn hợp đồng, hệ thống chỉ tạo một prompt đã ẩn danh để bạn tự sao chép "
            "sang ChatGPT, không tự tạo hợp đồng hay file DOCX. Kết quả là thông tin hỗ trợ, không thay thế "
            "ý kiến của luật sư trong tình huống rủi ro cao."
        )
        return _base_response(request, answer=answer, intent="CAPABILITY_QUESTION")
    if is_greeting(request.question):
        return _base_response(
            request,
            answer="Xin chào! Bạn có thể hỏi một vấn đề pháp lý trực tiếp hoặc chọn tài liệu trong workspace để tôi phân tích.",
            intent="GREETING",
        )
    if is_thank_you(request.question):
        return _base_response(
            request,
            answer="Rất vui vì đã hỗ trợ được bạn. Nếu cần, bạn có thể hỏi tiếp hoặc chọn tài liệu khác trong workspace.",
            intent="THANK_YOU",
        )
    return None


def build_contract_prompt_response(request: RagQueryRequest) -> RagQueryResponse:
    return build_drafting_response(request)
