from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import Mock, patch

from app.services.gemini_client import GeminiClient
from app.services.gemini_service import GeminiService


def _response(text: str = "ok") -> SimpleNamespace:
    return SimpleNamespace(
        text=text,
        usage_metadata=SimpleNamespace(
            prompt_token_count=11,
            candidates_token_count=3,
            total_token_count=14,
        ),
    )


def test_project_wrapper_uses_google_genai_models_api() -> None:
    sdk_client = Mock()
    sdk_client.models.generate_content.return_value = _response("  answer  ")

    with patch("app.services.gemini_client.build_genai_client", return_value=sdk_client):
        client = GeminiClient(api_key="secret", model="gemini-test", max_output_tokens=256)
        result = client.generate_text(system_prompt="system", user_prompt="question")

    assert result.text == "answer"
    assert result.model == "gemini-test"
    assert result.prompt_tokens == 11
    assert result.completion_tokens == 3
    assert result.total_tokens == 14
    call = sdk_client.models.generate_content.call_args.kwargs
    assert call["model"] == "gemini-test"
    assert call["contents"] == "question"
    assert call["config"].system_instruction == "system"
    assert call["config"].max_output_tokens == 256


def test_project_wrapper_reuses_clients_and_falls_back_to_next_model() -> None:
    sdk_client = Mock()
    sdk_client.models.generate_content.side_effect = [RuntimeError("primary unavailable"), _response("fallback")]

    with patch("app.services.gemini_client.build_genai_client", return_value=sdk_client) as factory:
        client = GeminiClient(
            api_key="secret",
            model="gemini-primary",
            fallback_model="gemini-fallback",
        )
        first_result = client.generate_text(system_prompt="system", user_prompt="question")

    assert factory.call_count == 1
    assert first_result.text == "fallback"
    assert first_result.model == "gemini-fallback"
    assert [call.kwargs["model"] for call in sdk_client.models.generate_content.call_args_list] == [
        "gemini-primary",
        "gemini-fallback",
    ]


def test_project_wrapper_retries_incomplete_max_tokens_response() -> None:
    sdk_client = Mock()
    max_tokens_response = _response("partial sentence")
    max_tokens_response.candidates = [SimpleNamespace(finish_reason="MAX_TOKENS")]
    complete_response = _response("complete answer")
    complete_response.candidates = [SimpleNamespace(finish_reason="STOP")]
    sdk_client.models.generate_content.side_effect = [max_tokens_response, complete_response]

    with patch("app.services.gemini_client.build_genai_client", return_value=sdk_client):
        client = GeminiClient(
            api_key="secret",
            model="gemini-2.5-flash",
            max_output_tokens=4096,
            thinking_budget=512,
        )
        result = client.generate_text(system_prompt="system", user_prompt="question")

    assert result.text == "complete answer"
    assert result.finish_reason == "STOP"
    assert sdk_client.models.generate_content.call_count == 2
    first_config = sdk_client.models.generate_content.call_args_list[0].kwargs["config"]
    retry_config = sdk_client.models.generate_content.call_args_list[1].kwargs["config"]
    assert first_config.max_output_tokens == 4096
    assert first_config.thinking_config.thinking_budget == 512
    assert retry_config.max_output_tokens == 8192


def test_gemini_service_uses_new_sync_and_streaming_apis() -> None:
    sdk_client = Mock()
    sdk_client.models.generate_content.return_value = _response("review")
    sdk_client.models.generate_content_stream.return_value = [
        SimpleNamespace(text="part 1"),
        SimpleNamespace(text="part 2"),
    ]
    service = GeminiService()
    service.client = sdk_client
    service.model_name = "gemini-test"

    answer = service.generate_review_answer("question", [], [])
    events = list(service.stream_review_answer("question", [], []))

    assert answer == "review"
    assert sdk_client.models.generate_content.call_args.kwargs["model"] == "gemini-test"
    assert sdk_client.models.generate_content_stream.call_args.kwargs["model"] == "gemini-test"
    assert any('"delta": "part 1"' in event for event in events)
    assert any('"answer": "part 1part 2"' in event for event in events)
