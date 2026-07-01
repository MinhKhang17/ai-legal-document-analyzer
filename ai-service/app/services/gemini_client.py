from __future__ import annotations

import json
import logging
import random
import time
from dataclasses import dataclass
from typing import Any
from urllib import error, request


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class GeminiResponse:
    text: str | None
    error: str | None


class GeminiClient:
    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        base_url: str = "https://generativelanguage.googleapis.com/v1beta",
        timeout_seconds: float = 30.0,
        max_output_tokens: int = 8192,
        max_retries: int = 4,
        retry_backoff_seconds: float = 2.0,
    ) -> None:
        self.api_keys = [k.strip() for k in api_key.replace(";", ",").split(",") if k.strip()]
        self.current_key_index = 0
        self.api_key = self.api_keys[0] if self.api_keys else ""
        self.model = model.strip()
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self.max_output_tokens = max_output_tokens
        self.max_retries = max(0, int(max_retries))
        self.retry_backoff_seconds = max(0.0, float(retry_backoff_seconds))

    def _get_current_key(self) -> str:
        if not self.api_keys:
            return ""
        return self.api_keys[self.current_key_index]

    def _rotate_key(self) -> None:
        if len(self.api_keys) > 1:
            self.current_key_index = (self.current_key_index + 1) % len(self.api_keys)
            logger.info("Rotated Gemini API key to index %d: %s...", self.current_key_index, self._get_current_key()[:10])

    def generate_text(self, *, system_prompt: str, user_prompt: str) -> GeminiResponse:
        if not self.api_keys or not self.model:
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

        last_error: str | None = None
        keys_tried = set()

        while len(keys_tried) < len(self.api_keys):
            active_key = self._get_current_key()
            keys_tried.add(active_key)

            req = request.Request(
                f"{self.base_url}/models/{self.model}:generateContent",
                data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                headers={
                    "x-goog-api-key": active_key,
                    "Content-Type": "application/json",
                },
                method="POST",
            )

            success = False
            for attempt in range(self.max_retries + 1):
                try:
                    with request.urlopen(req, timeout=self.timeout_seconds) as response:
                        body = json.loads(response.read().decode("utf-8"))
                    success = True
                    break
                except error.HTTPError as exc:
                    last_error = self._format_http_error(exc)
                    logger.warning("Gemini HTTP error %s with key %s...: %s", exc.code, active_key[:10], exc.reason)
                    if exc.code == 429:
                        if len(self.api_keys) > 1:
                            logger.info("Key hit 429, rotating key...")
                            self._rotate_key()
                            break
                    if not self._should_retry_http_status(exc.code, attempt):
                        return GeminiResponse(text=None, error=last_error)
                    self._sleep_backoff(attempt)
                except error.URLError as exc:
                    last_error = f"Gemini request failed: {exc.reason}"
                    logger.warning("Gemini URL error: %s", exc.reason)
                    if not self._should_retry_exception(attempt):
                        return GeminiResponse(text=None, error=last_error)
                    self._sleep_backoff(attempt)
                except TimeoutError:
                    last_error = "Gemini request timed out"
                    logger.warning("Gemini request timed out after %s seconds", self.timeout_seconds)
                    if not self._should_retry_exception(attempt):
                        return GeminiResponse(text=None, error=last_error)
                    self._sleep_backoff(attempt)
                except json.JSONDecodeError:
                    logger.warning("Gemini returned invalid JSON")
                    return GeminiResponse(text=None, error="Gemini returned invalid JSON")

            if success:
                break
        else:
            return GeminiResponse(text=None, error=last_error or "All Gemini API keys failed")

        text = self._extract_text(body)
        if not text:
            return GeminiResponse(text=None, error="Gemini response missing text content")
        return GeminiResponse(text=text, error=None)

    def _should_retry_http_status(self, status_code: int, attempt: int) -> bool:
        if attempt >= self.max_retries:
            return False
        return status_code in {429, 500, 503, 504}

    def _should_retry_exception(self, attempt: int) -> bool:
        return attempt < self.max_retries

    def _sleep_backoff(self, attempt: int) -> None:
        delay = self.retry_backoff_seconds * (2**attempt)
        if delay <= 0:
            return
        jitter = random.uniform(0.0, min(1.0, delay * 0.1))
        time.sleep(delay + jitter)

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
