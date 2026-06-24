from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass
from typing import Protocol

from app.core.config import settings
from app.services.gemini_client import GeminiClient


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class LlmResponse:
    answer: str | None
    risk_level: str
    confidence_score: float | None = None
    should_suggest_ticket: bool = False
    suggestion_type: str = "NONE"
    suggestion_reason: str | None = None
    missing_information: str | None = None
    legal_domain: str | None = None
    user_action_hint: str = "CONTINUE_CHAT"
    error: str | None = None


class RagLlmClient(Protocol):
    def generate(self, *, system_prompt: str, user_prompt: str) -> LlmResponse:
        raise NotImplementedError


class GeminiRagLlmClient:
    def __init__(self) -> None:
        self._client = GeminiClient(
            api_key=settings.gemini_api_key,
            model=settings.gemini_model,
            base_url=settings.gemini_base_url,
            timeout_seconds=settings.gemini_timeout_seconds,
            max_output_tokens=settings.gemini_max_output_tokens,
            max_retries=settings.gemini_max_retries,
            retry_backoff_seconds=settings.gemini_retry_backoff_seconds,
        )

    def generate(self, *, system_prompt: str, user_prompt: str) -> LlmResponse:
        response = self._client.generate_text(system_prompt=system_prompt, user_prompt=user_prompt)
        if response.error or not response.text:
            return LlmResponse(answer=None, risk_level="UNKNOWN", error=response.error or "LLM returned empty response")

        payload = self._extract_json(response.text)
        if payload is None:
            return LlmResponse(answer=response.text.strip(), risk_level="UNKNOWN", error=None)

        answer = str(payload.get("answer") or payload.get("response") or response.text).strip()
        risk_level = str(payload.get("riskLevel") or payload.get("risk_level") or "UNKNOWN").strip().upper()
        if risk_level not in {"LOW", "MEDIUM", "HIGH", "NEED_EXPERT", "UNKNOWN"}:
            risk_level = "UNKNOWN"
        confidence_score = payload.get("confidenceScore", payload.get("confidence_score"))
        should_suggest_ticket = bool(payload.get("shouldSuggestTicket", payload.get("should_suggest_ticket", False)))
        suggestion_type = str(payload.get("suggestionType") or payload.get("suggestion_type") or "NONE").strip().upper()
        suggestion_reason = payload.get("suggestionReason") or payload.get("suggestion_reason")
        missing_information = payload.get("missingInformation") or payload.get("missing_information")
        legal_domain = payload.get("legalDomain") or payload.get("legal_domain")
        user_action_hint = str(payload.get("userActionHint") or payload.get("user_action_hint") or "CONTINUE_CHAT").strip().upper()
        return LlmResponse(
            answer=answer or None,
            risk_level=risk_level,
            confidence_score=self._to_float(confidence_score),
            should_suggest_ticket=should_suggest_ticket,
            suggestion_type=suggestion_type,
            suggestion_reason=str(suggestion_reason).strip() if suggestion_reason is not None else None,
            missing_information=str(missing_information).strip() if missing_information is not None else None,
            legal_domain=str(legal_domain).strip() if legal_domain is not None else None,
            user_action_hint=user_action_hint,
            error=None,
        )

    def _extract_json(self, text: str) -> dict[str, object] | None:
        compact = text.strip()
        if compact.startswith("```"):
            compact = re.sub(r"^```[a-zA-Z]*\n?", "", compact).strip()
            if compact.endswith("```"):
                compact = compact[:-3].strip()
        try:
            parsed = json.loads(compact)
        except json.JSONDecodeError:
            return None
        if isinstance(parsed, dict):
            return parsed
        return None

    def _to_float(self, value: object) -> float | None:
        try:
            if value is None:
                return None
            return float(value)
        except Exception:
            return None


class MockRagLlmClient:
    def generate(self, *, system_prompt: str, user_prompt: str) -> LlmResponse:
        return LlmResponse(answer=None, risk_level="UNKNOWN", error="LLM is not configured")


def build_default_llm_client() -> RagLlmClient:
    if settings.llm_provider.lower() == "gemini" and settings.gemini_api_key and settings.gemini_model:
        return GeminiRagLlmClient()
    return MockRagLlmClient()
