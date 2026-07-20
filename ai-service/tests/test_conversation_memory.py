import json

from app.schemas import ConversationMessage, RagQueryRequest
from app.services.conversation_memory_service import ConversationMemoryService, estimate_tokens
from app.services.llm_client import LlmResponse
from app.services.prompt_builder import build_user_prompt
from app.services.token_budget import PromptTokenBudget


class MemoryServiceWithoutGraph(ConversationMemoryService):
    def __init__(self):
        pass

    def retrieve_relevant_turns(self, request):
        return []


class CountingLlm:
    def __init__(self, raw_response=""):
        self.calls = 0
        self.raw_response = raw_response

    def generate(self, *, system_prompt, user_prompt):
        self.calls += 1
        return LlmResponse(
            answer=self.raw_response,
            raw_response=self.raw_response,
            risk_level="UNKNOWN",
        )


def make_request(**updates):
    payload = {
        "request_id": "req_memory",
        "user_id": "1",
        "workspace_id": "ws_1",
        "chat_session_id": "chat_1",
        "question": "Điều khoản thanh toán trước đây là gì?",
        "attached_document_ids": ["doc_1"],
    }
    payload.update(updates)
    return RagQueryRequest.model_validate(payload)


def test_summary_is_not_called_without_evicted_messages():
    llm = CountingLlm()
    prepared = MemoryServiceWithoutGraph().prepare(
        make_request(recent_history=[]),
        llm_client=llm,
        llm_enabled=True,
    )

    assert llm.calls == 0
    assert prepared.memory_update is None


def test_incremental_summary_forces_historical_findings_to_require_revalidation():
    summary = {
        "conversationGoal": "Review rental contract",
        "establishedFacts": [],
        "userRole": "TENANT",
        "activeDocuments": ["doc_1"],
        "userConcerns": ["late fee"],
        "previousFindings": [{
            "finding": "Late fee may be high",
            "documentId": "doc_1",
            "sourceMessageId": "msg_old",
            "citationIds": ["KB-1"],
            "requiresRevalidation": False,
        }],
        "openQuestions": [],
        "userPreferences": {},
    }
    llm = CountingLlm(json.dumps(summary))
    evicted = [{
        "messageId": "msg_old",
        "role": "ASSISTANT",
        "content": "Historical conclusion [KB-1]",
        "documentIds": ["doc_1"],
        "citationIds": ["KB-1"],
    }]

    prepared = MemoryServiceWithoutGraph().prepare(
        make_request(evicted_messages=evicted),
        llm_client=llm,
        llm_enabled=True,
    )

    assert llm.calls == 1
    assert prepared.memory_update is not None
    updated = json.loads(prepared.memory_update.summaryJson)
    assert updated["previousFindings"][0]["requiresRevalidation"] is True
    assert updated["previousFindings"][0]["sourceMessageId"] == "msg_old"
    assert estimate_tokens(prepared.memory_update.summaryJson) <= 1_000


def test_prompt_uses_hybrid_memory_sections_not_full_chat_history():
    prompt = build_user_prompt(
        "Current question",
        [],
        [],
        chat_history="SHOULD_NOT_APPEAR",
        conversation_summary='{"conversationGoal":"review"}',
        recent_history="USER: recent question",
        relevant_history="[turn-1] historical reference",
        token_budget=PromptTokenBudget(),
    )

    assert "CONVERSATION_SUMMARY:" in prompt
    assert "RECENT_HISTORY:" in prompt
    assert "RELEVANT_HISTORY:" in prompt
    assert "HISTORY_CITATION_SAFETY:" in prompt
    assert "CHAT_HISTORY:" not in prompt
    assert "SHOULD_NOT_APPEAR" not in prompt


def test_recent_history_is_bounded_to_3000_tokens():
    messages = [ConversationMessage(
        messageId=f"msg_{index}",
        role="USER" if index % 2 == 0 else "ASSISTANT",
        content="x" * 8_000,
    ) for index in range(8)]
    request = make_request(recent_history=[message.model_dump() for message in messages])

    prepared = MemoryServiceWithoutGraph().prepare(
        request,
        llm_client=CountingLlm(),
        llm_enabled=False,
    )

    assert estimate_tokens(prepared.recent_history) <= 3_010
