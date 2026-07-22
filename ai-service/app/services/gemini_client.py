from __future__ import annotations

import logging
from dataclasses import dataclass

from google import genai
from google.genai import types


logger = logging.getLogger(__name__)

_GOOGLE_API_BASE_URLS = {
    "https://generativelanguage.googleapis.com",
    "https://generativelanguage.googleapis.com/v1beta",
}


def build_genai_client(
    *,
    api_key: str,
    base_url: str = "https://generativelanguage.googleapis.com/v1beta",
    timeout_seconds: float = 30.0,
    max_retries: int = 4,
    retry_backoff_seconds: float = 2.0,
) -> genai.Client:
    """Create a configured Google Gen AI client.

    The SDK already targets the Gemini Developer API beta endpoint. A base URL is
    only passed for an explicitly configured proxy/custom endpoint.
    """
    retry_options = types.HttpRetryOptions(
        attempts=max(1, int(max_retries) + 1),
        initial_delay=max(0.0, float(retry_backoff_seconds)),
        exp_base=2.0,
        http_status_codes=[429, 500, 503, 504],
    )
    http_options_kwargs: dict[str, object] = {
        "timeout": max(1, int(float(timeout_seconds) * 1000)),
        "retry_options": retry_options,
    }
    normalized_base_url = base_url.rstrip("/")
    if normalized_base_url and normalized_base_url not in _GOOGLE_API_BASE_URLS:
        http_options_kwargs["base_url"] = normalized_base_url

    return genai.Client(
        api_key=api_key,
        http_options=types.HttpOptions(**http_options_kwargs),
    )


@dataclass(frozen=True)
class GeminiResponse:
    text: str | None
    error: str | None
    model: str | None = None
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0
    thoughts_tokens: int = 0
    finish_reason: str | None = None


class GeminiClient:
    """Project-level Gemini wrapper backed exclusively by ``google.genai``."""

    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        base_url: str = "https://generativelanguage.googleapis.com/v1beta",
        timeout_seconds: float = 30.0,
        max_output_tokens: int = 8192,
        thinking_budget: int = 512,
        max_retries: int = 4,
        retry_backoff_seconds: float = 2.0,
        fallback_model: str = "",
    ) -> None:
        self.api_keys = [key.strip() for key in api_key.replace(";", ",").split(",") if key.strip()]
        self.model = model.strip()
        self.fallback_model = fallback_model.strip()
        self.max_output_tokens = max_output_tokens
        self.thinking_budget = thinking_budget
        self._clients = [
            build_genai_client(
                api_key=key,
                base_url=base_url,
                timeout_seconds=timeout_seconds,
                max_retries=max_retries,
                retry_backoff_seconds=retry_backoff_seconds,
            )
            for key in self.api_keys
        ]

    def generate_text(self, *, system_prompt: str, user_prompt: str) -> GeminiResponse:
        if not self._clients or not self.model:
            return GeminiResponse(text=None, error="Gemini is not configured")

        models = [self.model]
        if self.fallback_model and self.fallback_model != self.model:
            models.append(self.fallback_model)

        last_error: str | None = None

        for active_model in models:
            for key_index, client in enumerate(self._clients):
                try:
                    token_limits = [self.max_output_tokens, max(8192, self.max_output_tokens * 2)]
                    for generation_attempt, max_output_tokens in enumerate(token_limits):
                        response = client.models.generate_content(
                            model=active_model,
                            contents=user_prompt,
                            config=self._build_generation_config(
                                active_model,
                                system_prompt,
                                max_output_tokens=max_output_tokens,
                            ),
                        )
                        finish_reason = self._get_finish_reason(response)
                        usage = response.usage_metadata
                        if finish_reason == "MAX_TOKENS" and generation_attempt == 0:
                            logger.warning(
                                "Gemini reached MAX_TOKENS for model %s; retrying with %d output tokens",
                                active_model,
                                token_limits[1],
                            )
                            continue

                        text = (response.text or "").strip()
                        usage_fields = {
                            "prompt_tokens": int(getattr(usage, "prompt_token_count", 0) or 0),
                            "completion_tokens": int(getattr(usage, "candidates_token_count", 0) or 0),
                            "total_tokens": int(getattr(usage, "total_token_count", 0) or 0),
                            "thoughts_tokens": int(getattr(usage, "thoughts_token_count", 0) or 0),
                            "finish_reason": finish_reason,
                        }
                        if finish_reason == "MAX_TOKENS":
                            last_error = "Gemini response was incomplete after retry (MAX_TOKENS)"
                            logger.warning(
                                "%s for model %s; trying the next configured model or API key",
                                last_error,
                                active_model,
                            )
                            break
                        if not text:
                            last_error = "Gemini response missing text content"
                            logger.warning(
                                "%s for model %s; trying the next configured model or API key",
                                last_error,
                                active_model,
                            )
                            break

                        return GeminiResponse(text=text, error=None, model=active_model, **usage_fields)
                except Exception as exc:
                    last_error = self._format_sdk_error(exc)
                    logger.warning(
                        "Gemini SDK request failed for model %s with key index %d: %s",
                        active_model,
                        key_index,
                        exc,
                    )

            if active_model != models[-1]:
                logger.warning("Gemini model %s failed; trying fallback model %s", active_model, models[-1])

        return GeminiResponse(
            text=None,
            error=last_error or "All Gemini models and API keys failed",
            model=models[-1],
        )

    def _build_generation_config(
        self,
        model: str,
        system_prompt: str,
        *,
        max_output_tokens: int,
    ) -> types.GenerateContentConfig:
        thinking_config = None
        if "gemini-2.5" in model.lower():
            thinking_config = types.ThinkingConfig(thinking_budget=max(0, self.thinking_budget))
        return types.GenerateContentConfig(
            system_instruction=system_prompt,
            temperature=0.0,
            max_output_tokens=max_output_tokens,
            thinking_config=thinking_config,
        )

    @staticmethod
    def _get_finish_reason(response: object) -> str | None:
        candidates = getattr(response, "candidates", None) or []
        if not candidates:
            return None
        finish_reason = getattr(candidates[0], "finish_reason", None)
        if finish_reason is None:
            return None
        return str(getattr(finish_reason, "value", finish_reason)).upper().split(".")[-1]

    def close(self) -> None:
        for client in self._clients:
            try:
                client.close()
            except Exception as exc:
                logger.debug("Failed to close Gemini client cleanly: %s", exc)

    @staticmethod
    def _format_sdk_error(exc: Exception) -> str:
        code = getattr(exc, "code", None)
        status = f"HTTP {code} " if code else ""
        detail = " ".join(str(exc).split())
        if len(detail) > 300:
            detail = detail[:300].rsplit(" ", 1)[0].strip() + "..."
        return f"Gemini request failed: {status}{detail}".strip()
