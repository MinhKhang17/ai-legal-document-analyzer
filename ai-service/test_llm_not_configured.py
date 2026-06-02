"""Smoke test: LLM not configured returns a clean error, never crashes.

Run: python test_llm_not_configured.py
"""
from app.core.errors import LLMNotConfiguredError
from app.services.llm_client import NoopLLMClient


def main() -> None:
    client = NoopLLMClient()
    assert client.configured is False

    raised = False
    try:
        client.generate("any prompt")
    except LLMNotConfiguredError as exc:
        raised = True
        print(f"[INFO] Got expected LLM_NOT_CONFIGURED: {exc.message}")

    assert raised, "NoopLLMClient.generate should raise LLMNotConfiguredError"
    print("[OK] LLM-not-configured handled cleanly (no crash)")


if __name__ == "__main__":
    main()
