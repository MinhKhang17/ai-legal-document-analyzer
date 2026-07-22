from __future__ import annotations

import re
from dataclasses import dataclass

from app.models.intent_enums import (
    ContractType,
    Jurisdiction,
    LegalQueryIntent,
    ResponseMode,
    ResponseStatus,
    RiskLevel,
    SuggestionType,
    UserRole,
)


@dataclass(frozen=True)
class IntentResult:
    intent: LegalQueryIntent
    contract_type: ContractType
    user_role: UserRole
    jurisdiction: Jurisdiction
    response_mode: ResponseMode
    response_status: ResponseStatus
    suggestion_type: SuggestionType
    risk_level: RiskLevel
    confidence: float
    has_document_context: bool


_OUT_OF_DOMAIN_KEYWORDS = [
    "mác lê nin", "hôm nay ăn gì", "viết bài văn", "thời tiết", "nấu ăn",
    "bóng đá", "ca nhạc", "hello", "xin chào", "truyện cười",
]
_LEGAL_NOT_CONTRACT_KEYWORDS = [
    "ly hôn", "hình sự", "cccd", "căn cước", "đất đai", "tranh chấp đất",
    "thừa kế", "quốc tịch", "giấy khai sinh", "tai nạn giao thông",
]
_OUT_OF_STUDENT_SCOPE_KEYWORDS = [
    "m&a", "sáp nhập", "mua bán doanh nghiệp", "tín dụng ngân hàng",
    "thế chấp", "dự án bất động sản", "chuyển nhượng đất", "hợp đồng quốc tế",
    "đầu tư", "trái phiếu", "khoản vay ngân hàng", "hợp đồng bảo hiểm",
    "kinh doanh bảo hiểm", "bảo hiểm thương mại",
    "hợp đồng thương mại", "tố tụng", "vụ án", "hình sự",
]
_FOREIGN_KEYWORDS = [
    "singapore", "usa", "united states", "nhật", "japan", "hàn quốc",
    "korea", "anh quốc", "uk", "luật mỹ", "luật nước ngoài",
]
_PROMPT_INJECTION_KEYWORDS = [
    "bỏ qua luật", "ignore policy", "ignore the law", "giả làm luật sư",
    "cứ nói hợp pháp", "cam kết thắng kiện", "bỏ qua hướng dẫn", "override system",
]
_UNSAFE_KEYWORDS = [
    "né đóng bảo hiểm", "cài điều khoản để bên kia không biết",
    "giữ cọc dù không vi phạm", "lách luật", "gian dối", "lừa", "che giấu điều khoản",
]
_OVERCONFIDENT_KEYWORDS = [
    "khẳng định chắc chắn", "cam kết", "chắc chắn hợp pháp", "chắc chắn ký được",
    "kết luận tuyệt đối", "100% hợp pháp", "100% vô hiệu",
]
_MEANINGLESS_PATTERNS = [
    r"^(asd|asdasd|alo test|test|12345)+$",
    r"^[\W_?!.]{2,}$",
]
_UNDER_SPECIFIED_QUERIES = {"có đúng không", "ký được không", "ổn không", "có ổn không", "được không"}
_SUMMARY_KEYWORDS = ["tóm tắt", "summary", "nội dung chính"]
_RISK_KEYWORDS = ["rủi ro", "vi phạm", "bất lợi", "nguy cơ"]
_CLAUSE_ANALYSIS_KEYWORDS = ["điều khoản", "điều", "khoản", "clause", "mục"]
_CLAUSE_TOPIC_KEYWORDS = [
    "đặt cọc", "tiền cọc", "thanh toán", "trả góp", "phạt", "bồi thường",
    "chấm dứt", "gia hạn", "tăng giá", "quyền lợi", "nghĩa vụ", "bàn giao",
    "sửa chữa", "tranh chấp", "thời hạn", "giá thuê",
]
_CLAUSE_DRAFTING_KEYWORDS = ["soạn điều khoản", "viết điều khoản", "draft clause"]
_CLAUSE_REVISION_KEYWORDS = ["sửa điều khoản", "chỉnh điều khoản", "viết lại điều khoản"]
_MISSING_KEYWORDS = ["thiếu điều khoản", "cần bổ sung", "thiếu gì", "còn thiếu"]
_SIGNING_KEYWORDS = ["có nên ký", "ký được không", "nên ký không", "quyết định ký"]
_INFO_EXTRACTION_KEYWORDS = [
    "trích thông tin", "trích xuất", "trích xuất điều khoản", "trích điều khoản",
    "rút trích", "rút thông tin", "lấy thông tin", "extract clause", "extract terms",
    "bên nào", "thời hạn", "giá bao nhiêu",
]
_REVIEW_KEYWORDS = ["rà soát", "phân tích hợp đồng", "review hợp đồng", "kiểm tra hợp đồng"]
_VIETNAM_KEYWORDS = ["việt nam", "bl ds", "bộ luật dân sự", "luật việt nam"]
_LEGAL_KB_QUESTION_KEYWORDS = [
    "quy định", "pháp luật", "theo luật", "có được", "được phép", "có quyền",
    "tăng giá", "đặt cọc", "trả góp", "chấm dứt", "bồi thường", "phạt vi phạm",
    "quyền lợi", "nghĩa vụ", "thời hạn", "giá thuê", "thuê nhà", "nhà ở",
    "tài liệu", "tham khảo", "văn bản", "căn cứ", "nghị định", "bộ luật", "thông tư",
    "nguồn tham khảo", "tài liệu khác", "văn bản khác",
    "bảo hiểm tai nạn lao động", "bảo hiểm xã hội", "bảo hiểm y tế",
    "bảo hiểm thất nghiệp", "tai nạn lao động", "bệnh nghề nghiệp",
]


def _normalize(text: str) -> str:
    cleaned = text.lower().strip()
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned


def _contains_any(text: str, keywords: list[str]) -> bool:
    return any(keyword in text for keyword in keywords)


def detect_contract_type(question: str) -> ContractType:
    q = _normalize(question)
    if any(key in q for key in ["thuê trọ", "thuê phòng", "thuê nhà", "bên thuê", "bên cho thuê"]):
        return ContractType.RENTAL
    if any(key in q for key in ["thực tập", "internship", "intern"]):
        return ContractType.INTERNSHIP
    if any(key in q for key in ["cộng tác viên", "collaborator"]):
        return ContractType.COLLABORATOR
    if any(key in q for key in [
        "hợp đồng lao động", "người lao động", "làm thêm", "bán thời gian", "part-time",
    ]):
        return ContractType.PART_TIME_EMPLOYMENT
    if any(key in q for key in ["dịch vụ", "freelance", "thiết kế", "viết bài", "gia công nhỏ"]):
        return ContractType.FREELANCE_SERVICE
    if any(key in q for key in ["mua bán", "bán laptop", "bán xe", "tài sản nhỏ", "đặt mua"]):
        return ContractType.SMALL_ASSET_SALE
    if any(key in q for key in ["giấy vay", "vay tiền", "mượn tiền", "cho vay tiền"]):
        return ContractType.PERSONAL_LOAN
    if _contains_any(q, _OUT_OF_STUDENT_SCOPE_KEYWORDS):
        return ContractType.OUT_OF_STUDENT_SCOPE
    return ContractType.UNKNOWN


def detect_user_role(question: str) -> UserRole:
    q = _normalize(question)
    if "bên a" in q:
        return UserRole.PARTY_A
    if "bên b" in q:
        return UserRole.PARTY_B
    if any(key in q for key in ["người thuê", "bên thuê", "tôi thuê"]):
        return UserRole.TENANT
    if any(key in q for key in ["người cho thuê", "bên cho thuê", "tôi cho thuê", "chủ trọ"]):
        return UserRole.LANDLORD
    if any(key in q for key in ["người vay", "bên vay", "tôi vay"]):
        return UserRole.BORROWER
    if any(key in q for key in ["người cho vay", "bên cho vay", "tôi cho vay"]):
        return UserRole.LENDER
    return UserRole.UNKNOWN


def detect_jurisdiction(question: str) -> Jurisdiction:
    q = _normalize(question)
    if _contains_any(q, _FOREIGN_KEYWORDS):
        return Jurisdiction.FOREIGN
    if _contains_any(q, _VIETNAM_KEYWORDS) or any(word in q for word in ["hợp đồng", "điều khoản", "luật"]):
        return Jurisdiction.VIETNAM
    return Jurisdiction.UNKNOWN


def _review_intent_for_contract_type(contract_type: ContractType) -> LegalQueryIntent:
    mapping = {
        ContractType.RENTAL: LegalQueryIntent.STUDENT_RENTAL_CONTRACT_REVIEW,
        ContractType.PART_TIME_EMPLOYMENT: LegalQueryIntent.PART_TIME_OR_INTERNSHIP_CONTRACT_REVIEW,
        ContractType.INTERNSHIP: LegalQueryIntent.PART_TIME_OR_INTERNSHIP_CONTRACT_REVIEW,
        ContractType.COLLABORATOR: LegalQueryIntent.PART_TIME_OR_INTERNSHIP_CONTRACT_REVIEW,
        ContractType.FREELANCE_SERVICE: LegalQueryIntent.SMALL_SERVICE_CONTRACT_REVIEW,
        ContractType.SMALL_ASSET_SALE: LegalQueryIntent.SMALL_SALE_CONTRACT_REVIEW,
        ContractType.PERSONAL_LOAN: LegalQueryIntent.PERSONAL_LOAN_NOTE_REVIEW,
    }
    return mapping.get(contract_type, LegalQueryIntent.UNDER_SPECIFIED_LEGAL_QUERY)


def _fallback_document_review_intent(question: str) -> LegalQueryIntent:
    q = _normalize(question)
    if _contains_any(q, _SUMMARY_KEYWORDS):
        return LegalQueryIntent.CONTRACT_SUMMARY
    if _contains_any(q, _MISSING_KEYWORDS):
        return LegalQueryIntent.MISSING_CLAUSE_CHECK
    return LegalQueryIntent.CONTRACT_RISK_ANALYSIS


def detect_intent(
    question: str,
    *,
    has_user_chunks: bool = False,
    has_knowledge_chunks: bool = False,
    conversation_context: str = "",
) -> IntentResult:
    q = _normalize(question)
    context_q = _normalize(conversation_context)
    combined_context = f"{context_q} {q}".strip()
    contract_type = detect_contract_type(q)
    if contract_type == ContractType.UNKNOWN and context_q:
        contract_type = detect_contract_type(combined_context)
    user_role = detect_user_role(q)
    if user_role == UserRole.UNKNOWN and context_q:
        user_role = detect_user_role(combined_context)
    jurisdiction = detect_jurisdiction(q)
    if jurisdiction == Jurisdiction.UNKNOWN and context_q:
        jurisdiction = detect_jurisdiction(combined_context)

    if not q or any(re.fullmatch(pattern, q) for pattern in _MEANINGLESS_PATTERNS):
        return IntentResult(
            intent=LegalQueryIntent.INVALID_OR_MEANINGLESS_QUERY,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.ASK_FOR_INFORMATION,
            response_status=ResponseStatus.NEED_MORE_INFORMATION,
            suggestion_type=SuggestionType.ASK_MORE_FACTS,
            risk_level=RiskLevel.UNKNOWN,
            confidence=0.98,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _PROMPT_INJECTION_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.PROMPT_INJECTION_OR_POLICY_BYPASS,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.REFUSAL,
            response_status=ResponseStatus.UNSAFE_REQUEST,
            suggestion_type=SuggestionType.REFUSE_AND_REDIRECT,
            risk_level=RiskLevel.HIGH,
            confidence=0.99,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _UNSAFE_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.UNSAFE_LEGAL_REQUEST,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.REFUSAL,
            response_status=ResponseStatus.UNSAFE_REQUEST,
            suggestion_type=SuggestionType.REFUSE_AND_REDIRECT,
            risk_level=RiskLevel.CRITICAL,
            confidence=0.99,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _OVERCONFIDENT_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.OVERCONFIDENT_LEGAL_CONCLUSION_REQUEST,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.SAFE_REDIRECT,
            response_status=ResponseStatus.NEED_MORE_INFORMATION,
            suggestion_type=SuggestionType.ASK_MORE_FACTS,
            risk_level=RiskLevel.HIGH,
            confidence=0.95,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _OUT_OF_DOMAIN_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.OUT_OF_DOMAIN_GENERAL_QUERY,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.SAFE_REDIRECT,
            response_status=ResponseStatus.OUT_OF_SCOPE,
            suggestion_type=SuggestionType.REDIRECT_TO_SUPPORTED_SCOPE,
            risk_level=RiskLevel.NONE,
            confidence=0.97,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _LEGAL_NOT_CONTRACT_KEYWORDS) and not has_knowledge_chunks:
        return IntentResult(
            intent=LegalQueryIntent.LEGAL_BUT_NOT_CONTRACT_SCOPE,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.SAFE_REDIRECT,
            response_status=ResponseStatus.OUT_OF_SCOPE,
            suggestion_type=SuggestionType.REDIRECT_TO_SUPPORTED_SCOPE,
            risk_level=RiskLevel.MEDIUM,
            confidence=0.95,
            has_document_context=has_user_chunks,
        )

    if (
        has_knowledge_chunks
        and not has_user_chunks
        and jurisdiction != Jurisdiction.FOREIGN
        and contract_type != ContractType.OUT_OF_STUDENT_SCOPE
        and (
            _contains_any(q, _LEGAL_NOT_CONTRACT_KEYWORDS)
            or _contains_any(q, _LEGAL_KB_QUESTION_KEYWORDS)
        )
    ):
        return IntentResult(
            intent=LegalQueryIntent.LEGAL_KB_QUESTION,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction if jurisdiction != Jurisdiction.UNKNOWN else Jurisdiction.VIETNAM,
            response_mode=ResponseMode.DIRECT_ANSWER,
            response_status=ResponseStatus.ANSWERABLE,
            suggestion_type=SuggestionType.DIRECT_ANSWER,
            risk_level=RiskLevel.LOW,
            confidence=0.86,
            has_document_context=False,
        )

    if contract_type == ContractType.OUT_OF_STUDENT_SCOPE:
        return IntentResult(
            intent=LegalQueryIntent.CONTRACT_TYPE_OUT_OF_STUDENT_SCOPE,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.LAWYER_ESCALATION,
            response_status=ResponseStatus.HIGH_RISK_REQUIRE_LAWYER,
            suggestion_type=SuggestionType.SUGGEST_LAWYER,
            risk_level=RiskLevel.HIGH,
            confidence=0.95,
            has_document_context=has_user_chunks,
        )

    if jurisdiction == Jurisdiction.FOREIGN:
        return IntentResult(
            intent=LegalQueryIntent.FOREIGN_LAW_QUERY,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.SAFE_REDIRECT,
            response_status=ResponseStatus.OUT_OF_KNOWLEDGE_BASE,
            suggestion_type=SuggestionType.SUGGEST_LAWYER,
            risk_level=RiskLevel.HIGH,
            confidence=0.94,
            has_document_context=has_user_chunks,
        )

    has_follow_up_context = has_user_chunks and bool(context_q)
    has_clause_topic = _contains_any(q, _CLAUSE_TOPIC_KEYWORDS)
    if (q in _UNDER_SPECIFIED_QUERIES or len(q.split()) <= 3) and not (
        has_user_chunks and (has_clause_topic or has_follow_up_context)
    ):
        return IntentResult(
            intent=LegalQueryIntent.UNDER_SPECIFIED_LEGAL_QUERY,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.ASK_FOR_INFORMATION,
            response_status=ResponseStatus.NEED_MORE_INFORMATION,
            suggestion_type=SuggestionType.ASK_MORE_FACTS,
            risk_level=RiskLevel.UNKNOWN,
            confidence=0.9,
            has_document_context=has_user_chunks,
        )

    if has_user_chunks and has_clause_topic:
        return IntentResult(
            intent=LegalQueryIntent.CLAUSE_ANALYSIS,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.DOCUMENT_BASED_ANALYSIS,
            response_status=ResponseStatus.PARTIALLY_ANSWERABLE,
            suggestion_type=SuggestionType.DIRECT_ANSWER,
            risk_level=RiskLevel.MEDIUM,
            confidence=0.88,
            has_document_context=True,
        )

    if _contains_any(q, _CLAUSE_REVISION_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.CLAUSE_REVISION,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.DIRECT_ANSWER,
            response_status=ResponseStatus.PARTIALLY_ANSWERABLE,
            suggestion_type=SuggestionType.SUGGEST_REVISE_CLAUSE,
            risk_level=RiskLevel.MEDIUM,
            confidence=0.86,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _CLAUSE_DRAFTING_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.CLAUSE_DRAFTING,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.DIRECT_ANSWER,
            response_status=ResponseStatus.PARTIALLY_ANSWERABLE,
            suggestion_type=SuggestionType.DIRECT_ANSWER,
            risk_level=RiskLevel.LOW,
            confidence=0.86,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _MISSING_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.MISSING_CLAUSE_CHECK,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.DOCUMENT_BASED_ANALYSIS if has_user_chunks else ResponseMode.ASK_FOR_INFORMATION,
            response_status=ResponseStatus.PARTIALLY_ANSWERABLE if has_user_chunks else ResponseStatus.INCOMPLETE_INPUT,
            suggestion_type=SuggestionType.ASK_UPLOAD_CONTRACT if not has_user_chunks else SuggestionType.SUGGEST_REVISE_CLAUSE,
            risk_level=RiskLevel.MEDIUM,
            confidence=0.88,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _CLAUSE_ANALYSIS_KEYWORDS) and any(word in q for word in ["phân tích", "xem", "kiểm tra", "rủi ro", "đánh giá"]):
        return IntentResult(
            intent=LegalQueryIntent.CLAUSE_ANALYSIS,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.DOCUMENT_BASED_ANALYSIS if has_user_chunks else ResponseMode.ASK_FOR_INFORMATION,
            response_status=ResponseStatus.PARTIALLY_ANSWERABLE if has_user_chunks else ResponseStatus.INCOMPLETE_INPUT,
            suggestion_type=SuggestionType.ASK_UPLOAD_CONTRACT if not has_user_chunks else SuggestionType.DIRECT_ANSWER,
            risk_level=RiskLevel.MEDIUM,
            confidence=0.87,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _SUMMARY_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.CONTRACT_SUMMARY,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.DOCUMENT_BASED_ANALYSIS if has_user_chunks else ResponseMode.ASK_FOR_INFORMATION,
            response_status=ResponseStatus.PARTIALLY_ANSWERABLE if has_user_chunks else ResponseStatus.INCOMPLETE_INPUT,
            suggestion_type=SuggestionType.ASK_UPLOAD_CONTRACT if not has_user_chunks else SuggestionType.DIRECT_ANSWER,
            risk_level=RiskLevel.LOW,
            confidence=0.86,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _INFO_EXTRACTION_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.CONTRACT_INFORMATION_EXTRACTION,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.DOCUMENT_BASED_ANALYSIS if has_user_chunks else ResponseMode.ASK_FOR_INFORMATION,
            response_status=ResponseStatus.PARTIALLY_ANSWERABLE if has_user_chunks else ResponseStatus.INCOMPLETE_INPUT,
            suggestion_type=SuggestionType.ASK_UPLOAD_CONTRACT if not has_user_chunks else SuggestionType.DIRECT_ANSWER,
            risk_level=RiskLevel.LOW,
            confidence=0.84,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _SIGNING_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.SIGNING_DECISION_SUPPORT,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.DOCUMENT_BASED_ANALYSIS if has_user_chunks else ResponseMode.ASK_FOR_INFORMATION,
            response_status=ResponseStatus.PARTIALLY_ANSWERABLE if has_user_chunks else ResponseStatus.NEED_MORE_INFORMATION,
            suggestion_type=SuggestionType.ASK_UPLOAD_CONTRACT if not has_user_chunks else SuggestionType.SUGGEST_NEGOTIATION,
            risk_level=RiskLevel.HIGH,
            confidence=0.85,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _RISK_KEYWORDS):
        return IntentResult(
            intent=LegalQueryIntent.CONTRACT_RISK_ANALYSIS,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.DOCUMENT_BASED_ANALYSIS if has_user_chunks else ResponseMode.ASK_FOR_INFORMATION,
            response_status=ResponseStatus.PARTIALLY_ANSWERABLE if has_user_chunks else ResponseStatus.INCOMPLETE_INPUT,
            suggestion_type=SuggestionType.ASK_UPLOAD_CONTRACT if not has_user_chunks else SuggestionType.DIRECT_ANSWER,
            risk_level=RiskLevel.MEDIUM,
            confidence=0.88,
            has_document_context=has_user_chunks,
        )

    if _contains_any(q, _REVIEW_KEYWORDS) or has_user_chunks:
        review_intent = _review_intent_for_contract_type(contract_type)
        if review_intent == LegalQueryIntent.UNDER_SPECIFIED_LEGAL_QUERY and has_user_chunks:
            review_intent = _fallback_document_review_intent(q)
        return IntentResult(
            intent=review_intent if review_intent != LegalQueryIntent.UNDER_SPECIFIED_LEGAL_QUERY else LegalQueryIntent.INCOMPLETE_DOCUMENT,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.DOCUMENT_BASED_ANALYSIS if review_intent != LegalQueryIntent.UNDER_SPECIFIED_LEGAL_QUERY else ResponseMode.ASK_FOR_INFORMATION,
            response_status=ResponseStatus.PARTIALLY_ANSWERABLE if review_intent != LegalQueryIntent.UNDER_SPECIFIED_LEGAL_QUERY else ResponseStatus.INCOMPLETE_INPUT,
            suggestion_type=SuggestionType.DIRECT_ANSWER if review_intent != LegalQueryIntent.UNDER_SPECIFIED_LEGAL_QUERY else SuggestionType.ASK_CONTRACT_TYPE,
            risk_level=RiskLevel.MEDIUM,
            confidence=0.82,
            has_document_context=has_user_chunks,
        )

    if contract_type != ContractType.UNKNOWN:
        review_intent = _review_intent_for_contract_type(contract_type)
        return IntentResult(
            intent=review_intent,
            contract_type=contract_type,
            user_role=user_role,
            jurisdiction=jurisdiction,
            response_mode=ResponseMode.ASK_FOR_INFORMATION if not has_user_chunks else ResponseMode.DOCUMENT_BASED_ANALYSIS,
            response_status=ResponseStatus.INCOMPLETE_INPUT if not has_user_chunks else ResponseStatus.PARTIALLY_ANSWERABLE,
            suggestion_type=SuggestionType.ASK_UPLOAD_CONTRACT if not has_user_chunks else SuggestionType.DIRECT_ANSWER,
            risk_level=RiskLevel.LOW,
            confidence=0.78,
            has_document_context=has_user_chunks,
        )

    return IntentResult(
        intent=LegalQueryIntent.INSUFFICIENT_FACTS,
        contract_type=contract_type,
        user_role=user_role,
        jurisdiction=jurisdiction,
        response_mode=ResponseMode.ASK_FOR_INFORMATION,
        response_status=ResponseStatus.NEED_MORE_INFORMATION,
        suggestion_type=SuggestionType.ASK_MORE_FACTS,
        risk_level=RiskLevel.UNKNOWN,
        confidence=0.55,
        has_document_context=has_user_chunks,
    )
