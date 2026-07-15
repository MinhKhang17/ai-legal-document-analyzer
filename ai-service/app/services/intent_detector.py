"""Keyword-based intent detection and contract type detection for legal queries.

Classifies user queries into one of 14 LegalQueryIntent categories and detects
the contract type being discussed. Uses fast keyword matching (no LLM call needed).
"""
from __future__ import annotations

import logging
import re
from dataclasses import dataclass

from app.models.intent_enums import ContractType, LegalQueryIntent, ResponseMode

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class IntentResult:
    """Result of intent detection."""

    intent: LegalQueryIntent
    contract_type: ContractType
    response_mode: ResponseMode
    confidence: float  # 0.0 – 1.0
    has_document_context: bool  # Whether user has uploaded a document


# ─── keyword tables ──────────────────────────────────────────────────────────

_UNSAFE_KEYWORDS: list[str] = [
    "lách luật", "gian lận", "che giấu", "ép buộc", "lừa đảo",
    "để bên kia không phát hiện", "giấu điều khoản", "ép bên kia",
    "trốn thuế", "làm giả", "giả mạo", "qua mặt", "không cho biết",
    "bất lợi mà không phát hiện", "che mắt", "lừa", "ép", "giấu", "trốn nợ"
]

_SIGNING_KEYWORDS: list[str] = [
    "có nên ký", "nên ký không", "ký được không", "có nên ký không",
    "tôi có nên ký", "ký hợp đồng này", "quyết định ký",
    "should i sign", "should sign", "ký hay không",
    "nên đồng ý không", "chấp nhận không",
    "ký kết", "đồng ý ký kết", "nên ký kết", "quyết định ký kết", "có nên ký kết"
]

_VALIDITY_KEYWORDS: list[str] = [
    "hợp pháp", "vô hiệu", "có đúng luật", "trái luật", "vi phạm luật",
    "bất hợp pháp", "có hợp lệ", "có giá trị pháp lý",
    "legal", "valid", "invalid", "illegal", "void",
    "có hiệu lực", "mất hiệu lực", "vô hiệu hóa",
]

_MISSING_CLAUSE_KEYWORDS: list[str] = [
    "thiếu gì", "thiếu điều khoản", "cần bổ sung gì", "còn thiếu",
    "nên thêm gì", "chưa có điều khoản", "missing clause",
    "thiếu sót gì", "bổ sung thêm gì", "cần có thêm",
    "checklist", "điều khoản nào chưa có",
    "bổ sung", "thêm điều khoản", "chưa được đề cập", "chưa đề cập", "chưa có", "chưa xuất hiện",
    "bổ sung thêm", "cần bổ sung", "cần thêm"
]

_CLAUSE_ANALYSIS_KEYWORDS: list[str] = [
    "điều khoản", "clause", "phần về", "mục về", "điều về",
    "phần đặt cọc", "phần thanh toán", "phần chấm dứt",
    "phần phạt", "phần bất khả kháng", "phần bảo mật",
    "phần giải quyết tranh chấp", "phần quyền", "phần nghĩa vụ",
    "điều khoản phạt", "điều khoản thanh toán", "điều khoản chấm dứt",
    "phân tích phần", "phân tích điều",
]

_CLAUSE_TOPICS: list[str] = [
    "bảo mật", "thanh toán", "đặt cọc", "phạt", "chấm dứt", "bất khả kháng",
    "giải quyết tranh chấp", "bàn giao", "hiệu lực", "gia hạn", "quyền lợi", "nghĩa vụ"
]

_FULL_REVIEW_KEYWORDS: list[str] = [
    "rà soát", "phân tích hợp đồng", "review", "kiểm tra hợp đồng",
    "đánh giá hợp đồng", "xem xét hợp đồng", "check hợp đồng",
    "có rủi ro gì", "có vấn đề gì", "có ổn không", "có gì sai",
    "rà soát toàn bộ", "phân tích toàn bộ", "kiểm tra toàn diện",
    "full review", "contract review", "check giúp",
]

_CLAUSE_DRAFTING_KEYWORDS: list[str] = [
    "viết lại", "soạn điều khoản", "tạo điều khoản", "draft clause",
    "viết điều khoản", "sửa lại điều", "viết lại phần",
    "soạn lại", "chỉnh sửa điều khoản", "rewrite",
]

_TEMPORAL_KEYWORDS: list[str] = [
    "theo luật năm", "luật cũ", "luật mới", "so sánh luật",
    "version", "phiên bản", "trước đây", "hiện hành",
    "luật 2015", "luật 2005", "luật 2014", "nghị định mới",
]

_COMMERCIAL_KEYWORDS: list[str] = [
    "dòng tiền", "cashflow", "lợi nhuận", "giá trị thương mại",
    "đàm phán", "negotiation", "giá cả", "thương mại",
    "kinh doanh", "chi phí", "revenue", "profit",
]

_GENERAL_QUESTION_KEYWORDS: list[str] = [
    "là gì", "what is", "khái niệm", "giải thích",
    "quy định", "thủ tục", "làm sao", "làm thế nào",
    "how to", "how do", "tại sao", "why",
]

_NEED_MORE_INFO_KEYWORDS: list[str] = [
    "cung cấp thêm", "cần cung cấp gì", "tải tài liệu", "tải file", "tải lên", "upload",
    "gửi file", "đưa thêm dữ liệu", "khai báo thêm", "thiếu thông tin", "chưa tải"
]

_OUT_OF_SCOPE_KEYWORDS: list[str] = [
    "thời tiết", "bánh mì", "nấu ăn", "cách làm", "joke", "cười", "giải phương trình",
    "bóng đèn", "phát minh", "thủ đô", "hôm nay", "ngày mai", "chào", "hello",
    "thời tiết hà nội", "làm bánh mì", "phương trình bậc hai", "phát minh ra",
    "truyện cười", "cách làm bánh"
]

# ─── contract type keywords ──────────────────────────────────────────────────

_CONTRACT_TYPE_MAP: dict[str, ContractType] = {
    # Rental
    "thuê nhà": ContractType.RENTAL,
    "thuê trọ": ContractType.RENTAL,
    "thuê văn phòng": ContractType.RENTAL,
    "thuê mặt bằng": ContractType.RENTAL,
    "thuê phòng": ContractType.RENTAL,
    "cho thuê": ContractType.RENTAL,
    "rental": ContractType.RENTAL,
    "lease": ContractType.RENTAL,
    "tenancy": ContractType.RENTAL,
    # Labor
    "lao động": ContractType.LABOR,
    "nhân sự": ContractType.LABOR,
    "tuyển dụng": ContractType.LABOR,
    "employment": ContractType.LABOR,
    "labor": ContractType.LABOR,
    # Service
    "dịch vụ": ContractType.SERVICE,
    "service": ContractType.SERVICE,
    "tư vấn": ContractType.SERVICE,
    "outsource": ContractType.SERVICE,
    # Sale
    "mua bán": ContractType.SALE,
    "sale": ContractType.SALE,
    "purchase": ContractType.SALE,
    "bán hàng": ContractType.SALE,
    # NDA
    "bảo mật": ContractType.NDA,
    "nda": ContractType.NDA,
    "confidential": ContractType.NDA,
    "non-disclosure": ContractType.NDA,
    # Partnership
    "hợp tác": ContractType.PARTNERSHIP,
    "liên doanh": ContractType.PARTNERSHIP,
    "partnership": ContractType.PARTNERSHIP,
    "joint venture": ContractType.PARTNERSHIP,
    # Loan
    "vay": ContractType.LOAN,
    "cho vay": ContractType.LOAN,
    "tín dụng": ContractType.LOAN,
    "loan": ContractType.LOAN,
    # Authorization
    "ủy quyền": ContractType.AUTHORIZATION,
    "authorization": ContractType.AUTHORIZATION,
    "power of attorney": ContractType.AUTHORIZATION,
    # Construction
    "xây dựng": ContractType.CONSTRUCTION,
    "thi công": ContractType.CONSTRUCTION,
    "construction": ContractType.CONSTRUCTION,
    # Insurance
    "bảo hiểm": ContractType.INSURANCE,
    "insurance": ContractType.INSURANCE,
    # Transfer
    "chuyển nhượng": ContractType.TRANSFER,
    "transfer": ContractType.TRANSFER,
    # Donation
    "tặng cho": ContractType.DONATION,
    "donation": ContractType.DONATION,
}


# ─── intent → response mode mapping ──────────────────────────────────────────

_INTENT_TO_MODE: dict[LegalQueryIntent, ResponseMode] = {
    LegalQueryIntent.GENERAL_LEGAL_QUESTION: ResponseMode.GENERAL_GUIDANCE,
    LegalQueryIntent.CONTRACT_TYPE_ANALYSIS: ResponseMode.GENERAL_GUIDANCE,
    LegalQueryIntent.FULL_CONTRACT_REVIEW: ResponseMode.DOCUMENT_BASED_ANALYSIS,
    LegalQueryIntent.CLAUSE_ANALYSIS: ResponseMode.CLAUSE_LEVEL_ANALYSIS,
    LegalQueryIntent.MISSING_CLAUSE_CHECK: ResponseMode.CHECKLIST_ANALYSIS,
    LegalQueryIntent.LEGAL_VALIDITY_CHECK: ResponseMode.SUGGEST_LAWYER,
    LegalQueryIntent.SIGNING_DECISION_SUPPORT: ResponseMode.SUGGEST_LAWYER,
    LegalQueryIntent.CLAUSE_DRAFTING: ResponseMode.DIRECT_ANSWER,
    LegalQueryIntent.CLAUSE_REVISION: ResponseMode.DIRECT_ANSWER,
    LegalQueryIntent.TEMPORAL_LEGAL_ANALYSIS: ResponseMode.DIRECT_ANSWER,
    LegalQueryIntent.COMMERCIAL_CONTRACT_ANALYSIS: ResponseMode.DIRECT_ANSWER,
    LegalQueryIntent.OUT_OF_KNOWLEDGE_BASE: ResponseMode.OUT_OF_SCOPE_RESPONSE,
    LegalQueryIntent.NEED_MORE_INFO: ResponseMode.ASK_CLARIFYING_QUESTION,
    LegalQueryIntent.UNSAFE_LEGAL_REQUEST: ResponseMode.OUT_OF_SCOPE_RESPONSE,
}


def _normalize(text: str) -> str:
    """Lowercase, replace punctuation with spaces, collapse whitespace."""
    t = text.lower()
    for char in "?.,:;!()[]{}":
        t = t.replace(char, " ")
    return " ".join(t.split())


def _match_word(text: str, word: str) -> bool:
    padded = f" {text} "
    return f" {word} " in padded


def _match_any_words(text: str, keywords: list[str]) -> bool:
    padded = f" {text} "
    return any(f" {kw} " in padded for kw in keywords)


def detect_contract_type(question: str) -> ContractType:
    """Detect contract type from question text."""
    q = _normalize(question)
    for keyword, ctype in _CONTRACT_TYPE_MAP.items():
        if _match_word(q, keyword):
            return ctype
    return ContractType.UNKNOWN


def detect_intent(
    question: str,
    *,
    has_user_chunks: bool = False,
    has_knowledge_chunks: bool = False,
) -> IntentResult:
    """Detect intent from user question using keyword matching.

    Args:
        question: The user's question text.
        has_user_chunks: Whether user has uploaded documents with retrievable chunks.
        has_knowledge_chunks: Whether knowledge base has relevant chunks.

    Returns:
        IntentResult with classified intent, contract type, and response mode.
    """
    q = _normalize(question)
    contract_type = detect_contract_type(question)

    # ── Priority 1: Safety check ──
    if _match_any_words(q, _UNSAFE_KEYWORDS) or "trốn nợ" in q or "trốn thuế" in q or "lách luật" in q:
        logger.info("Intent detected: UNSAFE_LEGAL_REQUEST for query: %s", question[:80])
        return IntentResult(
            intent=LegalQueryIntent.UNSAFE_LEGAL_REQUEST,
            contract_type=contract_type,
            response_mode=ResponseMode.OUT_OF_SCOPE_RESPONSE,
            confidence=0.95,
            has_document_context=has_user_chunks,
        )

    # ── Priority 2: Out of Scope check ──
    if _match_any_words(q, _OUT_OF_SCOPE_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.OUT_OF_KNOWLEDGE_BASE,
            contract_type=contract_type,
            response_mode=ResponseMode.OUT_OF_SCOPE_RESPONSE,
            confidence=0.9,
            has_document_context=has_user_chunks,
        )

    # ── Priority 3: Need more info check ──
    is_indicative_of_upload = _match_any_words(q, ["vừa tải lên", "vừa upload", "vừa gửi", "tải lên rồi", "đã gửi"])
    if has_user_chunks and is_indicative_of_upload:
        # User explicitly says they just uploaded the file and has_user_chunks is True, do not trigger NEED_MORE_INFO
        pass
    elif _match_any_words(q, _NEED_MORE_INFO_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.NEED_MORE_INFO,
            contract_type=contract_type,
            response_mode=ResponseMode.ASK_CLARIFYING_QUESTION,
            confidence=0.9,
            has_document_context=has_user_chunks,
        )

    # ── Priority 4: Signing decision ──
    if _match_any_words(q, _SIGNING_KEYWORDS):
        mode = ResponseMode.SUGGEST_LAWYER if has_user_chunks else ResponseMode.ASK_CLARIFYING_QUESTION
        return IntentResult(
            intent=LegalQueryIntent.SIGNING_DECISION_SUPPORT,
            contract_type=contract_type,
            response_mode=mode,
            confidence=0.9,
            has_document_context=has_user_chunks,
        )

    # ── Priority 5: Temporal legal analysis ──
    has_year = len(re.findall(r"\b(19|20)\d{2}\b", q)) >= 1
    is_temporal = _match_any_words(q, _TEMPORAL_KEYWORDS) or "năm nào" in q or "từ năm" in q or (("luật" in q or "so sánh" in q) and ("cũ" in q or "mới" in q or "so sánh" in q or "thay đổi" in q) and (has_year or "đất đai" in q or "nhà ở" in q))
    if is_temporal:
        return IntentResult(
            intent=LegalQueryIntent.TEMPORAL_LEGAL_ANALYSIS,
            contract_type=contract_type,
            response_mode=ResponseMode.DIRECT_ANSWER,
            confidence=0.8,
            has_document_context=has_user_chunks,
        )

    # ── Priority 6: Legal validity ──
    is_general_how_to = _match_any_words(q, ["làm thế nào", "làm sao để", "quy trình", "thủ tục", "hướng dẫn"])
    if _match_any_words(q, _VALIDITY_KEYWORDS):
        if is_general_how_to and not has_user_chunks:
            # Bypass to GENERAL_LEGAL_QUESTION or CONTRACT_TYPE_ANALYSIS
            pass
        else:
            mode = ResponseMode.SUGGEST_LAWYER if has_user_chunks else ResponseMode.GENERAL_GUIDANCE
            return IntentResult(
                intent=LegalQueryIntent.LEGAL_VALIDITY_CHECK,
                contract_type=contract_type,
                response_mode=mode,
                confidence=0.85,
                has_document_context=has_user_chunks,
            )

    # ── Priority 7: Missing clause check ──
    if _match_any_words(q, _MISSING_CLAUSE_KEYWORDS) or "thiếu gì" in q or "bổ sung gì" in q:
        mode = ResponseMode.CHECKLIST_ANALYSIS if has_user_chunks else ResponseMode.ASK_CLARIFYING_QUESTION
        return IntentResult(
            intent=LegalQueryIntent.MISSING_CLAUSE_CHECK,
            contract_type=contract_type,
            response_mode=mode,
            confidence=0.85,
            has_document_context=has_user_chunks,
        )

    # ── Priority 8: Clause drafting / revision ──
    is_drafting_action = any(_match_word(q, verb) for verb in ["soạn", "viết", "draft", "viết lại", "sửa", "rewrite", "chỉnh sửa", "soạn thảo"])
    if _match_word(q, "tạo") and not any(prefix in q for prefix in ["đào tạo", "cải tạo", "sáng tạo", "kiến tạo"]):
        is_drafting_action = True
    is_clause_target = any(_match_word(q, target) for target in ["điều", "clause", "mục", "phần", "khoản"])
    if is_drafting_action and is_clause_target:
        intent = LegalQueryIntent.CLAUSE_REVISION if has_user_chunks else LegalQueryIntent.CLAUSE_DRAFTING
        return IntentResult(
            intent=intent,
            contract_type=contract_type,
            response_mode=ResponseMode.DIRECT_ANSWER,
            confidence=0.85,
            has_document_context=has_user_chunks,
        )

    if _match_any_words(q, _CLAUSE_DRAFTING_KEYWORDS):
        intent = LegalQueryIntent.CLAUSE_REVISION if has_user_chunks else LegalQueryIntent.CLAUSE_DRAFTING
        return IntentResult(
            intent=intent,
            contract_type=contract_type,
            response_mode=ResponseMode.DIRECT_ANSWER,
            confidence=0.85,
            has_document_context=has_user_chunks,
        )

    # ── Priority 9: Commercial analysis ──
    if _match_any_words(q, _COMMERCIAL_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.COMMERCIAL_CONTRACT_ANALYSIS,
            contract_type=contract_type,
            response_mode=ResponseMode.DIRECT_ANSWER,
            confidence=0.75,
            has_document_context=has_user_chunks,
        )

    # ── Priority 10: Clause analysis ──
    has_specific_clause_regex = bool(re.search(r"(điều|khoản|mục|clause|section)\s+\d+", q))
    is_clause_topic_query = has_user_chunks and _match_any_words(q, _CLAUSE_TOPICS) and _match_any_words(q, ["giải thích", "phân tích", "rà soát", "kiểm tra", "đánh giá", "rủi ro", "ổn không", "xem xét", "lưu ý"])
    
    if has_user_chunks and (_match_any_words(q, _CLAUSE_ANALYSIS_KEYWORDS) or has_specific_clause_regex or is_clause_topic_query):
        return IntentResult(
            intent=LegalQueryIntent.CLAUSE_ANALYSIS,
            contract_type=contract_type,
            response_mode=ResponseMode.CLAUSE_LEVEL_ANALYSIS,
            confidence=0.8,
            has_document_context=has_user_chunks,
        )

    # ── Priority 11: Contract type analysis ──
    _CONTRACT_TYPE_ANALYSIS_KEYWORDS = [
        "cần lưu ý gì", "lưu ý", "những điểm gì", "như thế nào", "cấu trúc", "mẫu", "là gì", "chú ý",
        "soạn thảo", "lập", "viết", "gồm những", "điều khoản chủ yếu", "nội dung"
    ]
    if contract_type != ContractType.UNKNOWN and not has_user_chunks and (_match_any_words(q, _GENERAL_QUESTION_KEYWORDS) or _match_any_words(q, _CONTRACT_TYPE_ANALYSIS_KEYWORDS)):
        return IntentResult(
            intent=LegalQueryIntent.CONTRACT_TYPE_ANALYSIS,
            contract_type=contract_type,
            response_mode=ResponseMode.GENERAL_GUIDANCE,
            confidence=0.85,
            has_document_context=has_user_chunks,
        )

    # ── Priority 12: Full contract review ──
    if _match_any_words(q, _FULL_REVIEW_KEYWORDS) or "rủi ro gì" in q or "bất lợi" in q or "chặt chẽ" in q:
        if has_user_chunks:
            return IntentResult(
                intent=LegalQueryIntent.FULL_CONTRACT_REVIEW,
                contract_type=contract_type,
                response_mode=ResponseMode.DOCUMENT_BASED_ANALYSIS,
                confidence=0.85,
                has_document_context=True,
            )
        else:
            # If no document is uploaded, but a contract type is detected, it's CONTRACT_TYPE_ANALYSIS
            if contract_type != ContractType.UNKNOWN:
                return IntentResult(
                    intent=LegalQueryIntent.CONTRACT_TYPE_ANALYSIS,
                    contract_type=contract_type,
                    response_mode=ResponseMode.GENERAL_GUIDANCE,
                    confidence=0.85,
                    has_document_context=False,
                )
            # No document → ask to upload or give general guidance
            return IntentResult(
                intent=LegalQueryIntent.FULL_CONTRACT_REVIEW,
                contract_type=contract_type,
                response_mode=ResponseMode.ASK_CLARIFYING_QUESTION,
                confidence=0.7,
                has_document_context=False,
            )

    # ── Priority 13: General legal question ──
    if _match_any_words(q, _GENERAL_QUESTION_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.GENERAL_LEGAL_QUESTION,
            contract_type=contract_type,
            response_mode=ResponseMode.GENERAL_GUIDANCE if has_knowledge_chunks else ResponseMode.DIRECT_ANSWER,
            confidence=0.7,
            has_document_context=has_user_chunks,
        )

    # ── Fallback: Determine based on context ──
    if has_user_chunks:
        return IntentResult(
            intent=LegalQueryIntent.FULL_CONTRACT_REVIEW,
            contract_type=contract_type,
            response_mode=ResponseMode.DOCUMENT_BASED_ANALYSIS,
            confidence=0.5,
            has_document_context=True,
        )

    return IntentResult(
        intent=LegalQueryIntent.GENERAL_LEGAL_QUESTION,
        contract_type=contract_type,
        response_mode=ResponseMode.GENERAL_GUIDANCE,
        confidence=0.4,
        has_document_context=False,
    )
