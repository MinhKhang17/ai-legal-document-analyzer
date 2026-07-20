from __future__ import annotations

from dataclasses import dataclass, field

from app.models.intent_enums import ContractType, LegalQueryIntent, UserRole


@dataclass
class CompletenessResult:
    is_complete: bool
    missing_items: list[str] = field(default_factory=list)
    questions_to_ask: list[str] = field(default_factory=list)


def check_completeness(
    intent: LegalQueryIntent,
    *,
    contract_type: ContractType = ContractType.UNKNOWN,
    user_role: UserRole = UserRole.UNKNOWN,
    has_user_chunks: bool = False,
    question: str = "",
    conversation_context: str = "",
) -> CompletenessResult:
    missing: list[str] = []
    questions: list[str] = []
    q = f"{conversation_context} {question}".lower().strip()

    review_like_intents = {
        LegalQueryIntent.STUDENT_RENTAL_CONTRACT_REVIEW,
        LegalQueryIntent.PART_TIME_OR_INTERNSHIP_CONTRACT_REVIEW,
        LegalQueryIntent.SMALL_SERVICE_CONTRACT_REVIEW,
        LegalQueryIntent.SMALL_SALE_CONTRACT_REVIEW,
        LegalQueryIntent.PERSONAL_LOAN_NOTE_REVIEW,
        LegalQueryIntent.CONTRACT_SUMMARY,
        LegalQueryIntent.CONTRACT_RISK_ANALYSIS,
        LegalQueryIntent.CLAUSE_ANALYSIS,
        LegalQueryIntent.MISSING_CLAUSE_CHECK,
        LegalQueryIntent.SIGNING_DECISION_SUPPORT,
        LegalQueryIntent.CONTRACT_INFORMATION_EXTRACTION,
        LegalQueryIntent.CLAUSE_REVISION,
    }

    if intent in review_like_intents and not has_user_chunks:
        missing.append("contractDocument")
        questions.append("Bạn hãy tải lên hợp đồng hoặc giấy vay để mình phân tích đúng nội dung cụ thể.")

    if contract_type == ContractType.UNKNOWN and not has_user_chunks and intent not in {
        LegalQueryIntent.OUT_OF_DOMAIN_GENERAL_QUERY,
        LegalQueryIntent.INVALID_OR_MEANINGLESS_QUERY,
        LegalQueryIntent.LEGAL_BUT_NOT_CONTRACT_SCOPE,
        LegalQueryIntent.LEGAL_KB_QUESTION,
        LegalQueryIntent.FOREIGN_LAW_QUERY,
        LegalQueryIntent.UNSAFE_LEGAL_REQUEST,
        LegalQueryIntent.PROMPT_INJECTION_OR_POLICY_BYPASS,
    }:
        missing.append("contractType")
        questions.append("Đây là loại hợp đồng nào: thuê trọ, làm thêm/thực tập, dịch vụ nhỏ, mua bán tài sản nhỏ hay giấy vay tiền?")

    if intent == LegalQueryIntent.SIGNING_DECISION_SUPPORT and user_role == UserRole.UNKNOWN:
        missing.append("userRole")
        questions.append("Bạn đang ở vai trò nào trong hợp đồng: bên A/B, người thuê/cho thuê, người vay/cho vay?")

    if intent in {LegalQueryIntent.CLAUSE_ANALYSIS, LegalQueryIntent.CLAUSE_REVISION}:
        has_target = any(
            key in q
            for key in [
                "điều khoản", "điều", "khoản", "thanh toán", "đặt cọc", "trả góp",
                "phạt", "bồi thường", "chấm dứt", "gia hạn", "tăng giá", "quyền lợi",
                "nghĩa vụ", "bàn giao", "sửa chữa", "tranh chấp", "thời hạn", "giá thuê",
            ]
        )
        if not has_target:
            missing.append("targetClause")
            questions.append("Bạn muốn kiểm tra hoặc sửa điều khoản nào cụ thể?")

    if intent == LegalQueryIntent.CLAUSE_DRAFTING:
        has_context = contract_type != ContractType.UNKNOWN or any(key in q for key in ["thanh toán", "đặt cọc", "bồi thường", "chấm dứt"])
        if not has_context:
            missing.append("draftingContext")
            questions.append("Bạn cần soạn điều khoản gì và dùng cho loại hợp đồng nào?")

    if intent in {
        LegalQueryIntent.UNDER_SPECIFIED_LEGAL_QUERY,
        LegalQueryIntent.INSUFFICIENT_FACTS,
        LegalQueryIntent.OVERCONFIDENT_LEGAL_CONCLUSION_REQUEST,
    }:
        missing.append("facts")
        questions.append("Bạn mô tả rõ hơn loại hợp đồng, vai trò của bạn và điều khoản hoặc vấn đề cần kiểm tra nhé.")

    return CompletenessResult(is_complete=not missing, missing_items=missing, questions_to_ask=questions)
