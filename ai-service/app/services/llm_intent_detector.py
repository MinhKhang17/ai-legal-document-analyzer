"""LLM-based intent detection using Gemini Structured Output.

Uses Gemini to classify user queries into LegalQueryIntent categories with
higher accuracy than keyword matching, especially for ambiguous or
misspelled queries. Falls back to keyword-based detection on failure.
"""
from __future__ import annotations

import json
import logging

from app.core.config import settings
from app.models.intent_enums import ContractType, LegalQueryIntent, ResponseMode
from app.services.gemini_client import GeminiClient
from app.services.intent_detector import IntentResult, detect_contract_type, detect_intent

logger = logging.getLogger(__name__)

# Mapping from LLM output string → enum value
_INTENT_MAP: dict[str, LegalQueryIntent] = {member.value: member for member in LegalQueryIntent}
_CONTRACT_TYPE_MAP: dict[str, ContractType] = {member.value: member for member in ContractType}

# Mapping intent → response mode (mirrors intent_detector.py)
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

_SYSTEM_PROMPT = """You are a legal query intent classifier for a Vietnamese legal document analyzer.

Given a user's question, classify it into exactly ONE intent and ONE contract type.

Available intents:
- GENERAL_LEGAL_QUESTION: General legal questions (e.g. "hợp đồng lao động là gì?")
- CONTRACT_TYPE_ANALYSIS: Questions about a specific contract type without an uploaded document
- FULL_CONTRACT_REVIEW: Request to review/analyze an entire uploaded contract
- CLAUSE_ANALYSIS: Analysis of a specific clause or section in an uploaded document
- MISSING_CLAUSE_CHECK: Check what clauses are missing from a contract
- LEGAL_VALIDITY_CHECK: Check if a contract/clause is legally valid
- SIGNING_DECISION_SUPPORT: Should I sign this contract?
- CLAUSE_DRAFTING: Draft a new clause (no document uploaded)
- CLAUSE_REVISION: Revise/rewrite a clause in an uploaded document
- TEMPORAL_LEGAL_ANALYSIS: Compare laws across time periods or versions
- COMMERCIAL_CONTRACT_ANALYSIS: Analysis focused on commercial/financial aspects
- OUT_OF_KNOWLEDGE_BASE: Question is unrelated to law/contracts
- NEED_MORE_INFO: User needs to provide more information
- UNSAFE_LEGAL_REQUEST: Request involves fraud, deception, or illegal activities

Available contract types:
- RENTAL, LABOR, SERVICE, SALE, NDA, PARTNERSHIP, LOAN, AUTHORIZATION, CONSTRUCTION, INSURANCE, TRANSFER, DONATION, UNKNOWN

Return ONLY valid JSON (no markdown, no explanation):
{"intent": "<INTENT>", "contract_type": "<CONTRACT_TYPE>", "confidence": <0.0-1.0>}"""


class LlmIntentDetector:
    """Intent detector using Gemini LLM for classification."""

    def __init__(self, *, api_key: str | None = None, model: str | None = None) -> None:
        self.api_key = api_key or settings.gemini_api_key
        self.model = model or settings.gemini_model

    def detect(
        self,
        question: str,
        *,
        has_user_chunks: bool = False,
        has_knowledge_chunks: bool = False,
        conversation_context: str | None = None,
    ) -> IntentResult | None:
        """Classify intent using Gemini LLM.

        Returns IntentResult on success, or None if LLM classification fails
        (caller should fall back to keyword-based detection).
        """
        if not self.api_key or not self.model:
            return None

        context_hint = ""
        if has_user_chunks:
            context_hint = "\n[Context: User HAS uploaded a document]"
        else:
            context_hint = "\n[Context: User has NOT uploaded any document]"

        if conversation_context:
            context_hint += f"\n[Conversation History:\n{conversation_context}\n]"

        user_prompt = f"Classify this query:{context_hint}\n\nQuery: {question}"

        client = GeminiClient(
            api_key=self.api_key,
            model=self.model,
            base_url=settings.gemini_base_url,
            timeout_seconds=min(settings.gemini_timeout_seconds, 15.0),  # Fast timeout for classification
            max_output_tokens=128,
            max_retries=1,  # Minimal retries for speed
            retry_backoff_seconds=0.5,
        )

        result = client.generate_text(
            system_prompt=_SYSTEM_PROMPT,
            user_prompt=user_prompt,
        )

        if result.error or not result.text:
            logger.debug("LLM intent detection failed: %s", result.error)
            return None

        return self._parse_response(
            result.text,
            question=question,
            has_user_chunks=has_user_chunks,
            has_knowledge_chunks=has_knowledge_chunks,
        )

    def _parse_response(
        self,
        text: str,
        *,
        question: str,
        has_user_chunks: bool,
        has_knowledge_chunks: bool,
    ) -> IntentResult | None:
        """Parse the JSON response from the LLM."""
        # Strip markdown fences if present
        clean = text.strip()
        if clean.startswith("```"):
            lines = clean.splitlines()
            if lines[0].startswith("```"):
                lines = lines[1:]
            if lines and lines[-1].startswith("```"):
                lines = lines[:-1]
            clean = "\n".join(lines).strip()

        try:
            parsed = json.loads(clean)
        except json.JSONDecodeError:
            logger.debug("LLM intent detection returned invalid JSON: %s", text[:200])
            return None

        intent_str = str(parsed.get("intent") or "").strip().upper()
        contract_type_str = str(parsed.get("contract_type") or "").strip().upper()
        confidence = float(parsed.get("confidence") or 0.5)

        # Map to enum values
        intent = _INTENT_MAP.get(intent_str)
        if intent is None:
            logger.debug("LLM returned unknown intent: %s", intent_str)
            return None

        contract_type = _CONTRACT_TYPE_MAP.get(contract_type_str, ContractType.UNKNOWN)
        response_mode = _INTENT_TO_MODE.get(intent, ResponseMode.DIRECT_ANSWER)

        # Adjust response mode based on document context
        if intent == LegalQueryIntent.FULL_CONTRACT_REVIEW and not has_user_chunks:
            response_mode = ResponseMode.ASK_CLARIFYING_QUESTION
        elif intent == LegalQueryIntent.SIGNING_DECISION_SUPPORT and not has_user_chunks:
            response_mode = ResponseMode.ASK_CLARIFYING_QUESTION
        elif intent == LegalQueryIntent.MISSING_CLAUSE_CHECK and not has_user_chunks:
            response_mode = ResponseMode.ASK_CLARIFYING_QUESTION

        logger.info(
            "LLM intent detection: intent=%s contract_type=%s confidence=%.2f",
            intent.value,
            contract_type.value,
            confidence,
        )

        return IntentResult(
            intent=intent,
            contract_type=contract_type,
            response_mode=response_mode,
            confidence=confidence,
            has_document_context=has_user_chunks,
        )


def detect_intent_smart(
    question: str,
    *,
    has_user_chunks: bool = False,
    has_knowledge_chunks: bool = False,
    conversation_context: str | None = None,
) -> IntentResult:
    """Smart intent detection: uses LLM if enabled, falls back to keyword matching.

    This is the main entry point that should be used instead of detect_intent() directly.
    """
    if settings.llm_intent_enabled:
        try:
            detector = LlmIntentDetector()
            result = detector.detect(
                question,
                has_user_chunks=has_user_chunks,
                has_knowledge_chunks=has_knowledge_chunks,
                conversation_context=conversation_context,
            )
            if result is not None:
                return result
            logger.debug("LLM intent detection returned None, falling back to keyword matching")
        except Exception as exc:
            logger.warning("LLM intent detection error, falling back to keyword matching: %s", exc)

    # Fallback to keyword-based detection
    return detect_intent(
        question,
        has_user_chunks=has_user_chunks,
        has_knowledge_chunks=has_knowledge_chunks,
        conversation_context=conversation_context or "",
    )
