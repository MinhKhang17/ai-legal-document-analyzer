from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any
from urllib import error, request


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
        max_output_tokens: int = 128,
    ) -> None:
        self.api_key = api_key.strip()
        self.model = model.strip()
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self.max_output_tokens = max_output_tokens

    def generate_text(self, *, system_prompt: str, user_prompt: str) -> GeminiResponse:
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
