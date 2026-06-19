from __future__ import annotations

import json
import time
import logging
from dataclasses import dataclass
from typing import Any
from urllib import error, request

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class GeminiResponse:
    text: str | None
    error: str | None
    retry_count: int = 0


class GeminiClient:
    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        base_url: str = "https://generativelanguage.googleapis.com/v1beta",
        timeout_seconds: float = 30.0,
        max_output_tokens: int = 128,
        max_retries: int = 3,
        retry_delays: list[int] | None = None,
    ) -> None:
        self.api_key = api_key.strip()
        self.model = model.strip()
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self.max_output_tokens = max_output_tokens
        self.max_retries = max_retries
        self.retry_delays = retry_delays or [2, 5, 10]  # exponential backoff

    def generate_text(self, *, system_prompt: str, user_prompt: str) -> GeminiResponse:
        """Generate text với retry logic cho Gemini free tier."""
        if not self.api_key or not self.model:
            return GeminiResponse(text=None, error="Gemini is not configured", retry_count=0)

        last_error = None
        retry_count = 0
        
        for attempt in range(self.max_retries + 1):
            try:
                result = self._make_request(system_prompt, user_prompt)
                
                if result.text:
                    return GeminiResponse(text=result.text, error=None, retry_count=retry_count)
                
                # No text but no error either - might be rate limit
                last_error = result.error
                
                # Check if it's a rate limit error
                if result.error and self._is_rate_limit_error(result.error):
                    if attempt < self.max_retries:
                        delay = self.retry_delays[min(attempt, len(self.retry_delays) - 1)]
                        logger.warning(
                            f"Gemini rate limited (attempt {attempt + 1}/{self.max_retries + 1}), "
                            f"retrying in {delay}s..."
                        )
                        time.sleep(delay)
                        retry_count += 1
                        continue
                
                # Other errors - return immediately
                return GeminiResponse(text=None, error=result.error, retry_count=retry_count)
                
            except Exception as e:
                last_error = f"Unexpected error: {str(e)}"
                logger.error(f"Gemini error on attempt {attempt + 1}: {last_error}")
                
                if attempt < self.max_retries:
                    delay = self.retry_delays[min(attempt, len(self.retry_delays) - 1)]
                    time.sleep(delay)
                    retry_count += 1
                    continue
        
        return GeminiResponse(
            text=None,
            error=last_error or "Max retries exceeded",
            retry_count=retry_count
        )
    
    def _make_request(self, system_prompt: str, user_prompt: str) -> GeminiResponse:
        """Make a single request to Gemini API."""
        if not self.api_key or not self.model:
            return GeminiResponse(text=None, error="Gemini is not configured")

        payload: dict[str, Any] = {
            "systemInstruction": {
                "parts": [{"text": system_prompt}],
            },
            "contents": [
                {
                    "role": "user",
                    "parts": [{"text": user_prompt}],
                }
            ],
            "generationConfig": {
                "temperature": 0.0,
                "maxOutputTokens": self.max_output_tokens,
            },
        }
        req = request.Request(
            f"{self.base_url}/models/{self.model}:generateContent",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "x-goog-api-key": self.api_key,
                "Content-Type": "application/json",
            },
            method="POST",
        )

        try:
            with request.urlopen(req, timeout=self.timeout_seconds) as response:
                body = json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exc:
            return GeminiResponse(text=None, error=self._format_http_error(exc))
        except error.URLError as exc:
            return GeminiResponse(text=None, error=f"Gemini request failed: {exc.reason}")
        except TimeoutError:
            return GeminiResponse(text=None, error="Gemini request timed out")
        except json.JSONDecodeError:
            return GeminiResponse(text=None, error="Gemini returned invalid JSON")

        text = self._extract_text(body)
        if not text:
            return GeminiResponse(text=None, error="Gemini response missing text content")
        return GeminiResponse(text=text, error=None)
    
    def _is_rate_limit_error(self, error_msg: str) -> bool:
        """Check if error is due to rate limiting."""
        if not error_msg:
            return False
        
        error_lower = error_msg.lower()
        rate_limit_indicators = [
            "429",
            "rate limit",
            "quota",
            "resource_exhausted",
            "too many requests",
        ]
        
        return any(indicator in error_lower for indicator in rate_limit_indicators)

    def _format_http_error(self, exc: error.HTTPError) -> str:
        try:
            body = exc.read().decode("utf-8", errors="replace").strip()
        except Exception:
            body = ""
        detail = f"HTTP {exc.code} {exc.reason}"
        if body:
            snippet = " ".join(body.split())
            if len(snippet) > 300:
                snippet = snippet[:300].rsplit(" ", 1)[0].strip() + "..."
            detail = f"{detail}: {snippet}"
        return f"Gemini request failed: {detail}"

    def _extract_text(self, body: dict[str, Any]) -> str | None:
        candidates = body.get("candidates") or []
        if not isinstance(candidates, list) or not candidates:
            return None

        content = (candidates[0] or {}).get("content") if isinstance(candidates[0], dict) else None
        if not isinstance(content, dict):
            return None

        parts = content.get("parts") or []
        if not isinstance(parts, list):
            return None

        texts: list[str] = []
        for part in parts:
            if isinstance(part, dict) and part.get("text"):
                texts.append(str(part["text"]))
        joined = "".join(texts).strip()
        return joined or None
