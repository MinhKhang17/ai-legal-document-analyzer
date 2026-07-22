from __future__ import annotations

import re
import unicodedata

from app.schemas import RagQueryRequest, RagQueryResponse, RagUsage


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


_REDACTION_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("EMAIL", re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.IGNORECASE)),
    ("PHONE", re.compile(r"(?<!\d)(?:\+?84|0)(?:[ .-]?\d){8,10}(?!\d)")),
    ("IDENTITY_NUMBER", re.compile(r"(?<!\d)\d{9,12}(?!\d)")),
    ("BANK_ACCOUNT", re.compile(r"(?i)(?:tai khoan|tài khoản|stk|account)\s*[:#-]?\s*\d{6,20}")),
    ("TAX_CODE", re.compile(r"(?i)(?:ma so thue|mã số thuế|mst|tax code)\s*[:#-]?\s*[\d-]{8,16}")),
    ("AMOUNT", re.compile(r"(?i)(?<!\w)\d[\d., ]{2,}\s*(?:vnd|đồng|dong|usd|\$)(?!\w)")),
)


def redact_sensitive_text(value: str) -> tuple[str, bool]:
    result = value
    changed = False
    counters: dict[str, int] = {}
    replacements: dict[tuple[str, str], str] = {}
    for label, pattern in _REDACTION_PATTERNS:
        def replace(match: re.Match[str], current_label: str = label) -> str:
            nonlocal changed
            changed = True
            key = (current_label, match.group(0).lower())
            if key not in replacements:
                counters[current_label] = counters.get(current_label, 0) + 1
                replacements[key] = f"[{current_label}_{counters[current_label]}]"
            return replacements[key]
        result = pattern.sub(replace, result)
    return result, changed


def build_contract_prompt_response(request: RagQueryRequest) -> RagQueryResponse:
    redacted_request, changed = redact_sensitive_text(request.question.strip())
    drafting_prompt = (
        "Bạn là trợ lý soạn thảo hợp đồng theo pháp luật Việt Nam. Hãy tạo BẢN NHÁP để người dùng rà soát, "
        "không khẳng định đây là tư vấn pháp lý cuối cùng. Yêu cầu của người dùng:\n"
        f"{redacted_request}\n\n"
        "Không suy đoán dữ liệu còn thiếu. Dùng các placeholder như [BÊN_A], [BÊN_B], [ĐỊA_CHỈ], [SỐ_TIỀN], "
        "[NGÀY_HIỆU_LỰC]. Trình bày: thông tin các bên, phạm vi, quyền/nghĩa vụ, thanh toán, thời hạn, "
        "chấm dứt, vi phạm/bồi thường, giải quyết tranh chấp và mục cần luật sư kiểm tra."
    )
    answer = (
        "Tôi đã tạo drafting prompt bên dưới để bạn tự kiểm tra và sao chép sang ChatGPT. "
        "Dữ liệu nhận diện trực tiếp phát hiện được đã được thay bằng placeholder; hãy rà soát lại trước khi gửi.\n\n"
        f"{drafting_prompt}"
    )
    return _base_response(
        request,
        answer=answer,
        intent="CONTRACT_PROMPT_GENERATION",
        actions=["COPY_PROMPT", "OPEN_CHATGPT"],
        drafting_prompt=drafting_prompt,
        redaction_required=changed,
    )
