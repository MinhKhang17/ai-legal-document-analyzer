"""Live connectivity test for the Gemini LLM client.

Requires a valid LLM_API_KEY in .env (LLM_PROVIDER=GEMINI). This actually calls
the Gemini API, so it only runs when a key is present.

Run: python test_gemini.py
"""
from app.core.config import LLMProvider, get_settings
from app.services.llm_client import build_llm_client


def main() -> None:
    settings = get_settings()

    if settings.llm_provider != LLMProvider.GEMINI:
        print(f"[SKIP] LLM_PROVIDER is {settings.llm_provider.value}, not GEMINI")
        return
    if not settings.llm_api_key or settings.llm_api_key.startswith("YOUR_"):
        print("[SKIP] LLM_API_KEY is not set (placeholder). Edit .env first.")
        return

    client = build_llm_client(settings)
    print(f"[INFO] provider={client.provider} model={client._model}")

    from app.core.errors import LLMGenerationError

    try:
        resp = client.generate(
            "Trả lời ngắn gọn bằng tiếng Việt: Đặt cọc trong hợp đồng là gì?"
        )
    except LLMGenerationError as exc:
        status = exc.details.get("status")
        # 429 = quota/rate limit, 503 = model overloaded. Both are transient
        # external conditions, not a code defect -> skip rather than fail.
        if status in (429, 503):
            print(f"[SKIP] Gemini temporarily unavailable (HTTP {status}). "
                  "This is a free-tier rate limit / overload, not a code error.")
            return
        raise

    print("[INFO] Gemini response:")
    print(resp.text.strip()[:500])
    assert resp.text.strip(), "Gemini returned empty text"
    print("[OK] Gemini live call succeeded")


if __name__ == "__main__":
    main()
