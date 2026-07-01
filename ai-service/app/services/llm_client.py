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
    raw_response: str | None = None
    analysis: dict | None = None


def sanitize_response(text: str) -> str:
    if not text:
        return ""
    
    # Strip citation markers like [K1], (K1), [U2], (U2), etc. to prevent them from showing in the user-facing text
    text = re.sub(r"\s*[\[\(][KU]\d+[\]\)]", "", text)
    
    # Clean up empty spaces and leftover commas/punctuation from stripped citations
    text = re.sub(r",\s*,", ",", text)
    text = re.sub(r"\s*,\s*(?=\s*,)", "", text)
    text = re.sub(r"\(\s*,+\s*", "(", text)
    text = re.sub(r"\[\s*,+\s*", "[", text)
    text = re.sub(r"\s*,+\s*\)", ")", text)
    text = re.sub(r"\s*,+\s*\]", "]", text)
    text = re.sub(r"\(\s*(?:như|ví dụ)?\s*\)", "", text, flags=re.IGNORECASE)
    text = re.sub(r"\[\s*(?:như|ví dụ)?\s*\]", "", text, flags=re.IGNORECASE)
    text = re.sub(r" {2,}", " ", text)
    
    # 1. Clean markdown code fences like ```json or ``` or ```html, etc.
    text = re.sub(r"```[a-zA-Z0-9]*\s*", "", text)
    text = text.replace("```", "")
    
    # 2. Check if the response looks like markdown content first
    #    If so, return it directly — do NOT try to parse as JSON
    stripped = text.strip()
    markdown_indicators = (
        stripped.startswith("#"),
        stripped.startswith("##"),
        stripped.startswith("- "),
        stripped.startswith("* "),
        stripped.startswith("**"),
        stripped.startswith("🟢"),
        stripped.startswith("🟡"),
        stripped.startswith("🟠"),
        stripped.startswith("🔴"),
        "\n## " in stripped,
        "\n### " in stripped,
        "\n- " in stripped,
        "KẾT QUẢ" in stripped,
        "PHÂN TÍCH" in stripped,
        "TÓM TẮT" in stripped,
        "KHUYẾN NGHỊ" in stripped,
    )
    if any(markdown_indicators):
        # Strip surrounding quotes if present
        if len(stripped) >= 2 and (
            (stripped.startswith('"') and stripped.endswith('"')) or
            (stripped.startswith("'") and stripped.endswith("'"))
        ):
            stripped = stripped[1:-1].strip()
        return stripped
    
    # 3. Check if the response is or contains JSON structure
    # Case A: Valid JSON dictionary
    if (stripped.startswith("{") and stripped.endswith("}")) or (stripped.startswith("[") and stripped.endswith("]")):
        try:
            parsed = json.loads(stripped)
            if isinstance(parsed, dict):
                candidate = parsed.get("answer") or parsed.get("response") or parsed.get("content")
                if candidate:
                    return sanitize_response(str(candidate))
                # Fallback to first non-empty string value
                for val in parsed.values():
                    if isinstance(val, str) and val.strip():
                        return sanitize_response(val)
        except Exception:
            pass

    # Case B: Truncated or invalid JSON containing "answer" or "response" field
    #         Only apply if text actually starts with { (looks like JSON)
    if stripped.startswith("{") and ('"answer"' in stripped or '"response"' in stripped):
        match = re.search(r'"(?:answer|response|content)"\s*:\s*"((?:[^"\\]|\\.)*)"', stripped)
        if match:
            try:
                val = json.loads(f'"{match.group(1)}"')
                return sanitize_response(val)
            except Exception:
                return sanitize_response(match.group(1))
        
        # Try unclosed key regex: `"answer": "..."` but unclosed at the end
        match_unclosed = re.search(r'"(?:answer|response|content)"\s*:\s*"\s*(.*)', stripped, re.DOTALL)
        if match_unclosed:
            val = match_unclosed.group(1).strip()
            val = re.sub(r'"\s*,\s*"[^"]*"\s*:\s*.*$', '', val, flags=re.DOTALL)
            val = re.sub(r'"\s*\}\s*$', '', val)
            val = re.sub(r'"\s*$', '', val)
            try:
                val_decoded = json.loads(f'"{val}"')
                return sanitize_response(val_decoded)
            except Exception:
                return sanitize_response(val)

        # Strip outer JSON brackets/keys if JSON parsing failed
        cleaned_brackets = re.sub(r'^\{\s*"?(?:answer|response|content)?"?\s*:\s*"?', '', stripped)
        cleaned_brackets = re.sub(r'"?\s*\}\s*$', '', cleaned_brackets)
        return sanitize_response(cleaned_brackets)

    # Strip surrounding quotes if present
    if len(stripped) >= 2 and (
        (stripped.startswith('"') and stripped.endswith('"')) or
        (stripped.startswith("'") and stripped.endswith("'"))
    ):
        stripped = stripped[1:-1].strip()
        
    return stripped


def extract_risk_level(text: str) -> str:
    # Look for risk level section or words in the text
    match = re.search(r'(?:RỦI RO|Risk Level)\s*:\s*([^\n\r]+)', text, re.IGNORECASE)
    if match:
        val = match.group(1).strip().upper()
        if "CAO" in val or "HIGH" in val:
            return "HIGH"
        if "TRUNG BÌNH" in val or "MEDIUM" in val or "MODERATE" in val:
            return "MEDIUM"
        if "THẤP" in val or "LOW" in val:
            return "LOW"
    
    # Fallback search anywhere in the text
    text_upper = text.upper()
    if "RỦI RO: CAO" in text_upper or "RỦI RO CAO" in text_upper:
        return "HIGH"
    if "RỦI RO: TRUNG BÌNH" in text_upper or "RỦI RO TRUNG BÌNH" in text_upper:
        return "MEDIUM"
    if "RỦI RO: THẤP" in text_upper or "RỦI RO THẤP" in text_upper:
        return "LOW"
        
    return "UNKNOWN"


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
            return LlmResponse(answer=None, risk_level="UNKNOWN", error=response.error or "LLM returned empty response", raw_response=response.text)

        raw_text = response.text
        sanitized_text = sanitize_response(raw_text)

        payload = self._extract_json(raw_text)
        if payload is None:
            risk_level = extract_risk_level(sanitized_text)
            analysis_data = parse_markdown_to_analysis(sanitized_text)
            return LlmResponse(
                answer=sanitized_text,
                risk_level=risk_level,
                raw_response=raw_text,
                error=None,
                analysis=analysis_data
            )

        answer = str(payload.get("answer") or payload.get("response") or raw_text).strip()
        sanitized_answer = sanitize_response(answer)
        
        risk_level = str(payload.get("riskLevel") or payload.get("risk_level") or "").strip().upper()
        if not risk_level or risk_level == "UNKNOWN":
            risk_level = extract_risk_level(sanitized_answer)
        if risk_level not in {"LOW", "MEDIUM", "HIGH", "NEED_EXPERT", "UNKNOWN"}:
            risk_level = "UNKNOWN"
        confidence_score = payload.get("confidenceScore", payload.get("confidence_score"))
        should_suggest_ticket = bool(payload.get("shouldSuggestTicket", payload.get("should_suggest_ticket", False)))
        suggestion_type = str(payload.get("suggestionType") or payload.get("suggestion_type") or "NONE").strip().upper()
        suggestion_reason = payload.get("suggestionReason") or payload.get("suggestion_reason")
        missing_information = payload.get("missingInformation") or payload.get("missing_information")
        legal_domain = payload.get("legalDomain") or payload.get("legal_domain")
        user_action_hint = str(payload.get("userActionHint") or payload.get("user_action_hint") or "CONTINUE_CHAT").strip().upper()
        
        analysis_raw = payload.get("analysis")
        analysis_data = None
        if isinstance(analysis_raw, dict):
            analysis_data = {
                "summary": analysis_raw.get("summary"),
                "keyClauses": [
                    {
                        "name": str(item.get("name") or "").strip(),
                        "content": str(item.get("content") or "").strip() or None,
                        "assessment": str(item.get("assessment") or "").strip() or None
                    } for item in (analysis_raw.get("keyClauses") or analysis_raw.get("key_clauses") or [])
                    if item and (item.get("name") or item.get("content"))
                ],
                "missingClauses": [
                    {
                        "name": str(item.get("name") or "").strip(),
                        "importance": str(item.get("importance") or "MEDIUM").upper(),
                        "reason": str(item.get("reason") or "").strip(),
                        "suggestedContent": str(item.get("suggestedContent") or item.get("suggested_content") or "").strip() or None
                    } for item in (analysis_raw.get("missingClauses") or analysis_raw.get("missing_clauses") or [])
                    if item and item.get("name")
                ],
                "riskItems": [
                    {
                        "title": str(item.get("title") or "").strip(),
                        "riskLevel": str(item.get("riskLevel") or item.get("risk_level") or "MEDIUM").upper(),
                        "description": str(item.get("description") or "").strip(),
                        "clause": str(item.get("clause") or "").strip() or None,
                        "recommendation": str(item.get("recommendation") or "").strip() or None
                    } for item in (analysis_raw.get("riskItems") or analysis_raw.get("risk_items") or [])
                    if item and item.get("title")
                ],
                "recommendations": [str(r).strip() for r in (analysis_raw.get("recommendations") or []) if r],
                "questionsToUser": [str(q).strip() for q in (analysis_raw.get("questionsToUser") or analysis_raw.get("questions_to_user") or []) if q]
            }
        else:
            # Fallback to parsing markdown answer if payload did not contain structured analysis
            analysis_data = parse_markdown_to_analysis(sanitized_answer)

        return LlmResponse(
            answer=sanitized_answer or None,
            risk_level=risk_level,
            confidence_score=self._to_float(confidence_score),
            should_suggest_ticket=should_suggest_ticket,
            suggestion_type=suggestion_type,
            suggestion_reason=str(suggestion_reason).strip() if suggestion_reason is not None else None,
            missing_information=str(missing_information).strip() if missing_information is not None else None,
            legal_domain=str(legal_domain).strip() if legal_domain is not None else None,
            user_action_hint=user_action_hint,
            error=None,
            raw_response=raw_text,
            analysis=analysis_data
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


def parse_markdown_to_analysis(text: str) -> dict | None:
    """Robust heuristic parser that converts structured Markdown response into structured analysis dict."""
    if not text:
        return None

    summary = ""
    key_clauses = []
    missing_clauses = []
    risk_items = []
    recommendations = []
    questions = []

    lines = text.split("\n")
    current_section = None

    for line in lines:
        line_strip = line.strip()
        if not line_strip:
            continue

        lower_line = line_strip.lower()
        
        # Section detection
        if "tóm tắt" in lower_line:
            current_section = "summary"
            continue
        elif "điều khoản chính" in lower_line or "các điều khoản quan trọng" in lower_line:
            current_section = "key_clauses"
            continue
        elif "điều khoản thiếu" in lower_line or "thiếu điều khoản" in lower_line:
            current_section = "missing_clauses"
            continue
        elif "rủi ro" in lower_line or "điểm rủi ro" in lower_line or "đánh giá rủi ro" in lower_line:
            current_section = "risk_items"
            continue
        elif "khuyến nghị" in lower_line or "đề xuất" in lower_line:
            current_section = "recommendations"
            continue
        elif "câu hỏi" in lower_line or "bổ sung thông tin" in lower_line or "thông tin còn thiếu" in lower_line:
            current_section = "questions"
            continue

        # Parse contents based on current active section
        if current_section == "summary":
            # Avoid repeating titles
            if not line_strip.startswith(("#", "**Tóm tắt")):
                summary += line_strip + " "

        elif current_section == "key_clauses":
            # Format: "- **Tên điều khoản**: Mô tả..."
            match = re.match(r"^[-*+\d.\s]*\*\*(.*?)\*\*\s*:\s*(.*)", line_strip)
            if match:
                key_clauses.append({
                    "name": match.group(1).strip(),
                    "content": match.group(2).strip(),
                    "assessment": None
                })
            else:
                match_bold = re.match(r"^[-*+\d.\s]*\*\*(.*?)\*\*(.*)", line_strip)
                if match_bold:
                    key_clauses.append({
                        "name": match_bold.group(1).strip(),
                        "content": match_bold.group(2).strip() or None,
                        "assessment": None
                    })

        elif current_section == "missing_clauses":
            # Format: "- **Tên điều khoản thiếu**: Lý do..."
            match = re.match(r"^[-*+\d.\s]*\*\*(.*?)\*\*\s*:\s*(.*)", line_strip)
            if match:
                name = match.group(1).strip()
                reason = match.group(2).strip()
                importance = "MEDIUM"
                if any(emoji in line_strip for emoji in ("🔴", "🔴CRITICAL", "HIGH", "cao", "quan trọng")):
                    importance = "HIGH"
                elif any(emoji in line_strip for emoji in ("🟢", "LOW", "thấp")):
                    importance = "LOW"
                missing_clauses.append({
                    "name": name,
                    "importance": importance,
                    "reason": reason,
                    "suggestedContent": None
                })

        elif current_section == "risk_items":
            # Format: "- 🔴 **Tên rủi ro**: Mô tả..."
            match = re.match(r"^[-*+\d.\s]*(🔴|🟢|🟡|🟠)?\s*\*\*(.*?)\*\*\s*:\s*(.*)", line_strip)
            if match:
                level_emoji = match.group(1)
                risk_level = "MEDIUM"
                if level_emoji == "🔴":
                    risk_level = "CRITICAL"
                elif level_emoji == "🟠":
                    risk_level = "HIGH"
                elif level_emoji == "🟢":
                    risk_level = "LOW"

                risk_items.append({
                    "title": match.group(2).strip(),
                    "riskLevel": risk_level,
                    "description": match.group(3).strip(),
                    "clause": None,
                    "recommendation": None
                })

        elif current_section == "recommendations":
            if line_strip.startswith(("-", "*", "1.", "2.", "3.", "4.", "5.", "6.", "7.", "8.", "9.")):
                clean_rec = re.sub(r"^[-*+\d.\s]*", "", line_strip).strip()
                recommendations.append(clean_rec)

        elif current_section == "questions":
            if line_strip.startswith(("-", "*", "1.", "2.", "3.", "4.", "5.", "6.", "7.", "8.", "9.")):
                clean_q = re.sub(r"^[-*+\d.\s]*", "", line_strip).strip()
                questions.append(clean_q)

    # Clean up summary
    cleaned_summary = " ".join(summary.split()).strip()

    return {
        "summary": cleaned_summary or None,
        "keyClauses": key_clauses,
        "missingClauses": missing_clauses,
        "riskItems": risk_items,
        "recommendations": recommendations,
        "questionsToUser": questions
    }


def build_default_llm_client() -> RagLlmClient:
    if settings.llm_provider.lower() == "gemini" and settings.gemini_api_key and settings.gemini_model:
        return GeminiRagLlmClient()
    return MockRagLlmClient()
