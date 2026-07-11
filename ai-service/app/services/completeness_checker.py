"""Input completeness checker for legal queries.

Checks whether the user has provided enough information for a given intent
to produce a meaningful analysis. Returns missing items and questions to ask.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field

from app.models.intent_enums import LegalQueryIntent

logger = logging.getLogger(__name__)


@dataclass
class CompletenessResult:
    """Result of input completeness check."""

    is_complete: bool
    missing_items: list[str] = field(default_factory=list)
    questions_to_ask: list[str] = field(default_factory=list)


def check_completeness(
    intent: LegalQueryIntent,
    *,
    has_user_chunks: bool = False,
    has_knowledge_chunks: bool = False,
    question: str = "",
) -> CompletenessResult:
    """Check if user has provided enough information for the detected intent.

    Args:
        intent: The detected LegalQueryIntent.
        has_user_chunks: Whether user documents are available.
        has_knowledge_chunks: Whether knowledge base chunks were found.
        question: The original question text (for role detection).

    Returns:
        CompletenessResult with is_complete flag, missing items, and questions.
    """
    missing: list[str] = []
    questions: list[str] = []
    q_lower = question.lower()

    # ── Intents that REQUIRE a document ──
    document_required_intents = {
        LegalQueryIntent.FULL_CONTRACT_REVIEW,
        LegalQueryIntent.CLAUSE_ANALYSIS,
        LegalQueryIntent.MISSING_CLAUSE_CHECK,
        LegalQueryIntent.LEGAL_VALIDITY_CHECK,
        LegalQueryIntent.SIGNING_DECISION_SUPPORT,
        LegalQueryIntent.CLAUSE_REVISION,
    }

    if intent in document_required_intents and not has_user_chunks:
        missing.append("document")
        questions.append(
            "Bạn chưa tải lên hợp đồng. Vui lòng tải lên file hợp đồng "
            "(PDF, DOCX, hoặc ảnh) để tôi có thể phân tích cụ thể."
        )

    # ── Signing decision needs user role ──
    if intent == LegalQueryIntent.SIGNING_DECISION_SUPPORT:
        role_indicators = [
            "bên a", "bên b", "bên thuê", "bên cho thuê",
            "người lao động", "người sử dụng lao động",
            "bên mua", "bên bán", "khách hàng", "nhà cung cấp",
            "tôi là", "vai trò",
        ]
        has_role = any(indicator in q_lower for indicator in role_indicators)
        if not has_role:
            missing.append("user_role")
            questions.append(
                "Bạn đang ở vai trò nào trong hợp đồng? "
                "(Ví dụ: Bên A/Bên B, Bên thuê/Bên cho thuê, "
                "Người lao động/Người sử dụng lao động)"
            )

    # ── Clause analysis needs target clause ──
    if intent == LegalQueryIntent.CLAUSE_ANALYSIS:
        specific_subjects = [
            "đặt cọc", "thanh toán", "chấm dứt", "phạt",
            "bảo mật", "tranh chấp", "bất khả kháng",
            "quyền", "nghĩa vụ", "bàn giao", "thuế",
            "gia hạn", "hiệu lực", "vi phạm", "đơn phương",
        ]
        has_specific_subject = any(subj in q_lower for subj in specific_subjects)
        
        import re
        # Check for patterns like "điều 5", "khoản 2", "mục iv", "phần a", etc.
        has_clause_with_number = bool(
            re.search(r"\b(?:điều|khoản|mục|phần|clause)\s*(?:\d+|[a-gA-G]|[iIvVxXyYdDmM]+)\b", q_lower)
        )
        
        if not (has_specific_subject or has_clause_with_number):
            missing.append("target_clause")
            questions.append(
                "Bạn muốn phân tích điều khoản nào cụ thể? "
                "(Ví dụ: đặt cọc, thanh toán, chấm dứt hợp đồng, phạt vi phạm...)"
            )

    # ── Clause drafting needs context ──
    if intent == LegalQueryIntent.CLAUSE_DRAFTING:
        context_indicators = [
            "thuê nhà", "lao động", "dịch vụ", "mua bán",
            "thanh toán", "phạt", "chấm dứt", "bảo mật",
        ]
        has_context = any(indicator in q_lower for indicator in context_indicators)
        if not has_context:
            missing.append("clause_context")
            questions.append(
                "Bạn cần soạn điều khoản cho loại hợp đồng nào "
                "và điều khoản cụ thể là gì? "
                "(Ví dụ: điều khoản thanh toán cho hợp đồng thuê nhà)"
            )

    # ── Temporal analysis needs time reference ──
    if intent == LegalQueryIntent.TEMPORAL_LEGAL_ANALYSIS:
        time_indicators = [
            "2015", "2014", "2005", "2019", "2020", "2023", "2024",
            "năm nào", "luật cũ", "luật mới", "trước đây", "hiện hành",
        ]
        has_time = any(indicator in q_lower for indicator in time_indicators)
        if not has_time:
            missing.append("time_reference")
            questions.append(
                "Bạn muốn so sánh theo thời điểm nào? "
                "(Ví dụ: so sánh Bộ luật Dân sự 2005 và 2015)"
            )

    is_complete = len(missing) == 0
    return CompletenessResult(
        is_complete=is_complete,
        missing_items=missing,
        questions_to_ask=questions,
    )
