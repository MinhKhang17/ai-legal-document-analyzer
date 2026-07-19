from __future__ import annotations

from dataclasses import dataclass

from app.models.intent_enums import LegalQueryIntent


def estimate_tokens(text: str | None) -> int:
    if not text:
        return 0
    return max(1, (len(text) + 3) // 4)


def truncate_to_token_budget(text: str, max_tokens: int) -> str:
    if max_tokens <= 0:
        return "[none]"
    if estimate_tokens(text) <= max_tokens:
        return text
    return text[: max_tokens * 4].rstrip() + "\n[truncated to token budget]"


@dataclass(frozen=True)
class PromptTokenBudget:
    system_prompt: int = 2_500
    conversation_summary: int = 1_000
    recent_history: int = 3_000
    relevant_history: int = 1_200
    user_document_context: int = 5_000
    legal_kb_context: int = 4_000
    output: int = 2_000


def budget_for_intent(intent: LegalQueryIntent) -> PromptTokenBudget:
    if intent == LegalQueryIntent.CONTRACT_SUMMARY:
        return PromptTokenBudget(user_document_context=7_000, legal_kb_context=0, output=1_800)
    if intent == LegalQueryIntent.LEGAL_KB_QUESTION:
        return PromptTokenBudget(user_document_context=1_200, legal_kb_context=6_000, output=1_800)
    if intent in {
        LegalQueryIntent.CONTRACT_RISK_ANALYSIS,
        LegalQueryIntent.STUDENT_RENTAL_CONTRACT_REVIEW,
        LegalQueryIntent.PART_TIME_OR_INTERNSHIP_CONTRACT_REVIEW,
        LegalQueryIntent.SMALL_SERVICE_CONTRACT_REVIEW,
        LegalQueryIntent.SMALL_SALE_CONTRACT_REVIEW,
        LegalQueryIntent.PERSONAL_LOAN_NOTE_REVIEW,
    }:
        return PromptTokenBudget(user_document_context=6_000, legal_kb_context=5_000, output=2_500)
    return PromptTokenBudget()
