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
        return LlmResponse(answer=answer or None, risk_level=risk_level, error=None)

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


class MockRagLlmClient:
    def generate(self, *, system_prompt: str, user_prompt: str) -> LlmResponse:
        return LlmResponse(answer=None, risk_level="UNKNOWN", error="LLM is not configured")


def build_default_llm_client() -> RagLlmClient:
    if settings.llm_provider.lower() == "gemini" and settings.gemini_api_key and settings.gemini_model:
        return GeminiRagLlmClient()
    return MockRagLlmClient()
