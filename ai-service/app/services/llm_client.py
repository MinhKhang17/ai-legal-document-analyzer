"""LLM client abstraction.

Provides a single interface (LLMClient.generate) with interchangeable
implementations selected by the LLM_PROVIDER environment variable:

  * NoopLLMClient  - default; raises LLMNotConfiguredError so callers can fall
                     back to deterministic graph-based analysis instead of
                     pretending an AI answered.
  * GeminiLLMClient - Google Generative Language API.
  * OllamaLLMClient - local Ollama server.
  * VLLMClient      - OpenAI-compatible vLLM server.

No API keys are hard-coded; everything comes from Settings. Implementations are
created lazily and never crash the app at import time.
"""
from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any, Dict, Optional

import httpx

from app.core.config import LLMProvider, Settings, get_settings
from app.core.errors import LLMGenerationError, LLMNotConfiguredError


@dataclass
class LLMResponse:
    text: str
    provider: str
    model: Optional[str] = None
    raw: Dict[str, Any] = field(default_factory=dict)


class LLMClient:
    """Abstract LLM client interface."""

    provider: str = "BASE"

    @property
    def configured(self) -> bool:
        return True

    def generate(self, prompt: str, **kwargs: Any) -> LLMResponse:  # pragma: no cover
        raise NotImplementedError


class NoopLLMClient(LLMClient):
    """Used when LLM_PROVIDER=NONE. Always signals not-configured."""

    provider = "NONE"

    @property
    def configured(self) -> bool:
        return False

    def generate(self, prompt: str, **kwargs: Any) -> LLMResponse:
        raise LLMNotConfiguredError(
            "No LLM provider configured. Set LLM_PROVIDER to GEMINI, OLLAMA or VLLM.",
            details={"provider": "NONE"},
        )


class GeminiLLMClient(LLMClient):
    """Google Generative Language API (Gemini) client.

    Uses the REST generateContent endpoint with the API key supplied via the
    `x-goog-api-key` header (kept out of the URL/query string). The default
    model is gemini-2.5-flash; override via LLM_MODEL.
    """

    provider = "GEMINI"

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._api_key = settings.llm_api_key
        self._model = settings.llm_model or "gemini-2.5-flash-lite"
        self._base = settings.gemini_base_url.rstrip("/")

    @property
    def configured(self) -> bool:
        return bool(self._api_key)

    def generate(self, prompt: str, **kwargs: Any) -> LLMResponse:
        if not self.configured:
            raise LLMNotConfiguredError(
                "GEMINI selected but LLM_API_KEY is missing",
                details={"provider": "GEMINI"},
            )
        url = f"{self._base}/v1beta/models/{self._model}:generateContent"
        headers = {
            "x-goog-api-key": self._api_key,
            "Content-Type": "application/json",
        }
        generation_config: Dict[str, Any] = {
            "temperature": kwargs.get("temperature", 0.2),
        }
        # When the caller wants strict JSON, ask Gemini for a JSON mime type.
        if kwargs.get("json_output"):
            generation_config["responseMimeType"] = "application/json"

        body = {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": generation_config,
        }

        # Retry transient errors (429 rate limit, 5xx overload) with backoff.
        retryable = {429, 500, 502, 503, 504}
        max_attempts = 3
        last_exc: Optional[Exception] = None
        data: Dict[str, Any] = {}
        for attempt in range(1, max_attempts + 1):
            try:
                with httpx.Client(timeout=90.0) as client:
                    resp = client.post(url, json=body, headers=headers)
                    resp.raise_for_status()
                    data = resp.json()
                break
            except httpx.HTTPStatusError as exc:
                status = exc.response.status_code if exc.response is not None else None
                last_exc = exc
                if status in retryable and attempt < max_attempts:
                    time.sleep(2 ** (attempt - 1))  # 1s, 2s
                    continue
                detail = exc.response.text[:500] if exc.response is not None else str(exc)
                raise LLMGenerationError(
                    "Gemini request failed",
                    details={"status": status, "body": detail, "attempts": attempt},
                ) from exc
            except httpx.HTTPError as exc:
                last_exc = exc
                if attempt < max_attempts:
                    time.sleep(2 ** (attempt - 1))
                    continue
                raise LLMGenerationError(
                    "Gemini request failed",
                    details={"cause": str(exc), "attempts": attempt},
                ) from exc

        # Handle prompt being blocked by safety filters.
        if not data.get("candidates"):
            feedback = data.get("promptFeedback", {})
            raise LLMGenerationError(
                "Gemini returned no candidates (possibly blocked)",
                details={"promptFeedback": feedback},
            )
        try:
            parts = data["candidates"][0]["content"]["parts"]
            text = "".join(p.get("text", "") for p in parts)
        except (KeyError, IndexError) as exc:
            raise LLMGenerationError(
                "Unexpected Gemini response shape", details={"cause": str(exc)}
            ) from exc
        return LLMResponse(text=text, provider=self.provider, model=self._model, raw=data)


class OllamaLLMClient(LLMClient):
    provider = "OLLAMA"

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._base = settings.ollama_base_url.rstrip("/")
        self._model = settings.llm_model or "llama3"

    @property
    def configured(self) -> bool:
        return bool(self._base)

    def generate(self, prompt: str, **kwargs: Any) -> LLMResponse:
        url = f"{self._base}/api/generate"
        body = {"model": self._model, "prompt": prompt, "stream": False}
        try:
            with httpx.Client(timeout=120.0) as client:
                resp = client.post(url, json=body)
                resp.raise_for_status()
                data = resp.json()
            return LLMResponse(
                text=data.get("response", ""),
                provider=self.provider,
                model=self._model,
                raw=data,
            )
        except httpx.HTTPError as exc:
            raise LLMGenerationError(
                "Ollama request failed", details={"cause": str(exc)}
            ) from exc


class VLLMClient(LLMClient):
    provider = "VLLM"

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._base = settings.vllm_base_url.rstrip("/")
        self._model = settings.llm_model or "default"
        self._api_key = settings.llm_api_key

    @property
    def configured(self) -> bool:
        return bool(self._base)

    def generate(self, prompt: str, **kwargs: Any) -> LLMResponse:
        url = f"{self._base}/v1/chat/completions"
        headers = {}
        if self._api_key:
            headers["Authorization"] = f"Bearer {self._api_key}"
        body = {
            "model": self._model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": kwargs.get("temperature", 0.2),
        }
        try:
            with httpx.Client(timeout=120.0) as client:
                resp = client.post(url, json=body, headers=headers)
                resp.raise_for_status()
                data = resp.json()
            text = data["choices"][0]["message"]["content"]
            return LLMResponse(text=text, provider=self.provider, model=self._model, raw=data)
        except httpx.HTTPError as exc:
            raise LLMGenerationError(
                "vLLM request failed", details={"cause": str(exc)}
            ) from exc
        except (KeyError, IndexError) as exc:
            raise LLMGenerationError(
                "Unexpected vLLM response shape", details={"cause": str(exc)}
            ) from exc


def build_llm_client(settings: Optional[Settings] = None) -> LLMClient:
    """Factory selecting the implementation based on LLM_PROVIDER."""
    settings = settings or get_settings()
    provider = settings.llm_provider
    if provider == LLMProvider.GEMINI:
        return GeminiLLMClient(settings)
    if provider == LLMProvider.OLLAMA:
        return OllamaLLMClient(settings)
    if provider == LLMProvider.VLLM:
        return VLLMClient(settings)
    return NoopLLMClient()


_client: Optional[LLMClient] = None


def get_llm_client() -> LLMClient:
    global _client
    if _client is None:
        _client = build_llm_client()
    return _client
