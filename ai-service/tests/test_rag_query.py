from __future__ import annotations

import unittest
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import app
from app.models.intent_enums import LegalQueryIntent
from app.models.knowledge_models import RetrievedChunk
from app.schemas import RagQueryRequest, RagQueryResponse
from app.services.llm_client import LlmResponse
from app.services.conversation_context import LegalConversationContextStore, is_follow_up_query
from app.services.query_builder import build_legal_search_query, build_legal_text_query, extract_recent_user_history
from app.services.rag_query_service import LlmGenerationError, RagQueryService
from app.services.retrieval_service import RagChunkHit, RetrievalService


class _FakeRetrievalService:
    def __init__(self):
        self.document_id = None

    def search_user_chunks(self, question: str, user_id: str, workspace_id: str, top_k: int, document_id: str | None = None, document_ids: list[str] | None = None):
        self.document_id = document_id or (document_ids[0] if document_ids else None)
        return [
            RagChunkHit(
                citationId="USER-1",
                sourceType="USER_DOCUMENT",
                score=0.91,
                chunkText="Điều 4: Bên thuê phải thanh toán đúng hạn.",
                documentId="doc-1",
                workspaceId=workspace_id,
                userId=user_id,
                fileName="hop-dong.pdf",
            )
        ]

    def search_knowledge_chunks(self, legal_search_query: str, top_k: int, query_text: str | None = None):
        return [
            RagChunkHit(
                citationId="KB-1",
                sourceType="SYSTEM_KB",
                score=0.88,
                chunkText="Bộ luật Dân sự quy định nghĩa vụ thực hiện đúng cam kết.",
                knowledgeDocumentId="law-1",
                lawName="Bộ luật Dân sự",
                legalDomain="contract_law",
            )
        ]


class _FakeLlmClient:
    def generate(self, *, system_prompt: str, user_prompt: str):
        return LlmResponse(
            answer="Kết luận: cần kiểm tra điều khoản thanh toán [USER-1] [KB-1].",
            risk_level="MEDIUM",
            used_knowledge_citation_ids=("KB-1",),
            used_user_citation_ids=("USER-1",),
            model="gemini-test",
            prompt_tokens=12,
            completion_tokens=8,
            total_tokens=20,
        )


class _CapturingLlmClient(_FakeLlmClient):
    def __init__(self):
        self.user_prompts: list[str] = []

    def generate(self, *, system_prompt: str, user_prompt: str):
        self.user_prompts.append(user_prompt)
        return super().generate(system_prompt=system_prompt, user_prompt=user_prompt)


class _TwoKbRetrievalService(_FakeRetrievalService):
    def search_knowledge_chunks(self, legal_search_query: str, top_k: int, query_text: str | None = None):
        return [
            RagChunkHit(
                citationId="KB-1",
                sourceType="SYSTEM_KB",
                score=0.88,
                chunkText="Quy định pháp luật thứ nhất.",
                lawCode="LAW-A",
            ),
            RagChunkHit(
                citationId="KB-2",
                sourceType="SYSTEM_KB",
                score=0.87,
                chunkText="Quy định pháp luật thứ hai.",
                lawCode="LAW-B",
            ),
        ]


class _KnowledgeOnlyRetrievalService(_FakeRetrievalService):
    def search_user_chunks(self, question: str, user_id: str, workspace_id: str, top_k: int, document_id: str | None = None):
        return []


class RagQueryTests(unittest.TestCase):
    def test_llm_preview_mode_returns_exact_prompts_without_calling_llm(self) -> None:
        llm_client = _CapturingLlmClient()
        service = RagQueryService(
            retrieval_service=_FakeRetrievalService(),
            llm_client=llm_client,
            llm_enabled=False,
        )

        response = service.query(
            RagQueryRequest(
                requestId="req-preview",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="chat-preview",
                question="Phan tich rui ro hop dong thue nha cua toi",
            )
        )

        self.assertFalse(response.llmExecuted)
        self.assertEqual(response.model, "prompt-preview")
        self.assertEqual(response.responseStatus, "PROMPT_PREVIEW")
        self.assertIn("===== SYSTEM PROMPT =====", response.answer)
        self.assertIn("===== USER PROMPT =====", response.answer)
        self.assertEqual(response.usage.totalTokens, 0)
        self.assertEqual(llm_client.user_prompts, [])
        self.assertTrue(response.systemPromptPreview)
        self.assertTrue(response.userPromptPreview)

    def test_rag_query_service_builds_response(self) -> None:
        retrieval_service = _FakeRetrievalService()
        service = RagQueryService(
            retrieval_service=retrieval_service,
            llm_client=_FakeLlmClient(),
        )
        response = service.query(
            RagQueryRequest(
                requestId="req-1",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="chat-1",
                documentId="doc-1",
                question="Hợp đồng có rủi ro gì?",
            )
        )

        self.assertEqual(response.requestId, "req-1")
        self.assertEqual(response.chatSessionId, "chat-1")
        self.assertEqual(response.riskLevel, "MEDIUM")
        self.assertEqual(response.retrievedUserChunks, 1)
        self.assertEqual(response.retrievedKnowledgeChunks, 1)
        self.assertEqual([item.citationId for item in response.citations], ["USER-1", "KB-1"])
        self.assertEqual(response.usedKnowledgeCitationIds, ["KB-1"])
        self.assertEqual(response.usedUserCitationIds, ["USER-1"])
        self.assertEqual(response.citations[0].excerpt, "Điều 4: Bên thuê phải thanh toán đúng hạn.")
        self.assertEqual(retrieval_service.document_id, "doc-1")
        self.assertEqual(response.model, "gemini-test")
        self.assertEqual(response.usage.totalTokens, 20)

    def test_follow_up_uses_latest_structured_snapshot_without_full_history(self) -> None:
        context_store = LegalConversationContextStore()
        llm_client = _CapturingLlmClient()
        service = RagQueryService(
            retrieval_service=_FakeRetrievalService(),
            llm_client=llm_client,
            context_store=context_store,
        )
        service.query(
            RagQueryRequest(
                requestId="req-initial",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="chat-follow-up",
                question="Phân tích rủi ro hợp đồng thuê nhà của tôi",
            )
        )

        response = service.query(
            RagQueryRequest(
                requestId="req-follow-up",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="chat-follow-up",
                chatHistory="ASSISTANT: FULL_OLD_ANSWER_SHOULD_NOT_BE_IN_PROMPT",
                question="Ở phần trên bạn dựa vào bộ luật nào?",
            )
        )

        self.assertEqual(response.requestId, "req-follow-up")
        self.assertEqual(len(llm_client.user_prompts), 2)
        follow_up_prompt = llm_client.user_prompts[-1]
        self.assertIn("SESSION_ANALYSIS_SNAPSHOT", follow_up_prompt)
        self.assertIn('"analysisId":"req-initial"', follow_up_prompt)
        self.assertIn('"claimLedger"', follow_up_prompt)
        self.assertIn("FOLLOW_UP_RULES", follow_up_prompt)
        self.assertNotIn("FULL_OLD_ANSWER_SHOULD_NOT_BE_IN_PROMPT", follow_up_prompt)
        snapshots = context_store.all_for_session("chat-follow-up")
        self.assertEqual([item.analysisId for item in snapshots], ["req-initial", "req-follow-up"])
        initial_snapshot = snapshots[0]
        self.assertEqual(initial_snapshot.sessionId, "chat-follow-up")
        self.assertEqual(initial_snapshot.userSources[0].id, "USER-1")
        self.assertEqual(initial_snapshot.kbSources[0].id, "KB-1")
        self.assertFalse(initial_snapshot.kbSources[0].isDirectlyApplicable)
        self.assertEqual(initial_snapshot.claimLedger[0].basedOnUserSources, ("USER-1",))
        self.assertEqual(initial_snapshot.claimLedger[0].basedOnKbSources, ("KB-1",))
        self.assertEqual(initial_snapshot.claimLedger[0].legalBasisStrength, "INDIRECT")
        self.assertEqual(initial_snapshot.lastAnswerSummary.citationsUsed, ("USER-1", "KB-1"))

    def test_follow_up_without_snapshot_returns_need_more_information(self) -> None:
        llm_client = _CapturingLlmClient()
        service = RagQueryService(
            retrieval_service=_FakeRetrievalService(),
            llm_client=llm_client,
            context_store=LegalConversationContextStore(),
        )

        response = service.query(
            RagQueryRequest(
                requestId="req-no-snapshot",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="empty-session",
                question="Vì sao điều khoản đó có rủi ro cao?",
            )
        )

        self.assertTrue(is_follow_up_query("Vì sao điều khoản đó có rủi ro cao?"))
        self.assertEqual(response.responseStatus, "NEED_MORE_INFORMATION")
        self.assertEqual(response.missingInputs, ["analysisSnapshot"])
        self.assertEqual(llm_client.user_prompts, [])

    def test_out_of_domain_query_short_circuits(self) -> None:
        service = RagQueryService(
            retrieval_service=_FakeRetrievalService(),
            llm_client=_FakeLlmClient(),
        )
        response = service.query(
            RagQueryRequest(
                requestId="req-2",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="chat-1",
                question="Hôm nay ăn gì?",
            )
        )

        self.assertEqual(response.intent, "OUT_OF_DOMAIN_GENERAL_QUERY")
        self.assertEqual(response.responseStatus, "OUT_OF_SCOPE")
        self.assertEqual(response.suggestionType, "REDIRECT_TO_SUPPORTED_SCOPE")
        self.assertFalse(response.shouldSuggestTicket)
        self.assertEqual(response.citations, [])

    def test_under_specified_query_asks_for_more_info(self) -> None:
        service = RagQueryService(
            retrieval_service=_FakeRetrievalService(),
            llm_client=_FakeLlmClient(),
        )
        response = service.query(
            RagQueryRequest(
                requestId="req-3",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="chat-1",
                question="Ký được không?",
            )
        )

        self.assertEqual(response.intent, "UNDER_SPECIFIED_LEGAL_QUERY")
        self.assertEqual(response.userActionHint, "PROVIDE_MORE_INFO")
        self.assertFalse(response.inputComplete)

    def test_short_clause_follow_up_uses_history_and_document_context(self) -> None:
        service = RagQueryService(
            retrieval_service=_FakeRetrievalService(),
            llm_client=_FakeLlmClient(),
        )

        response = service.query(
            RagQueryRequest(
                requestId="req-short-follow-up",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="chat-1",
                question="đặt cọc",
                chatHistory=(
                    "USER: Tôi là bên B trong hợp đồng thuê nhà.\n\n"
                    "ASSISTANT: Tôi đã ghi nhận vai trò của bạn."
                ),
            )
        )

        self.assertEqual(response.intent, "CLAUSE_ANALYSIS")
        self.assertEqual(response.contractType, "RENTAL")
        self.assertEqual(response.userRole, "PARTY_B")
        self.assertTrue(response.inputComplete)
        self.assertEqual(response.usedKnowledgeCitationIds, ["KB-1"])

    def test_general_legal_question_is_answered_from_kb_without_user_document(self) -> None:
        llm_client = SimpleNamespace(
            generate=lambda **_: LlmResponse(
                answer="Chủ nhà chỉ nên điều chỉnh giá theo thỏa thuận và căn cứ áp dụng [KB-1].",
                risk_level="LOW",
                confidence_score=0.85,
            )
        )
        service = RagQueryService(
            retrieval_service=_KnowledgeOnlyRetrievalService(),
            llm_client=llm_client,
        )

        response = service.query(
            RagQueryRequest(
                requestId="req-legal-kb-only",
                userId="user-1",
                workspaceId="ws-1",
                question="Chủ nhà có được tăng giá thuê không?",
            )
        )

        self.assertEqual(response.intent, "LEGAL_KB_QUESTION")
        self.assertTrue(response.inputComplete)
        self.assertEqual(response.responseStatus, "ANSWERABLE")
        self.assertEqual(response.usedKnowledgeCitationIds, ["KB-1"])
        self.assertEqual([citation.citationId for citation in response.citations], ["KB-1"])
        self.assertFalse(response.shouldSuggestTicket)

    def test_legal_recommendation_without_kb_citation_is_rejected(self) -> None:
        llm_client = SimpleNamespace(
            generate=lambda **_: LlmResponse(
                answer=(
                    "Quy định chung được giải thích tại đây [KB-1].\n\n"
                    "**Khuyến nghị:** Bạn nên thực hiện một hành động pháp lý ngay."
                ),
                risk_level="LOW",
                confidence_score=0.9,
            )
        )
        service = RagQueryService(
            retrieval_service=_KnowledgeOnlyRetrievalService(),
            llm_client=llm_client,
        )

        response = service.query(
            RagQueryRequest(
                requestId="req-ungrounded-recommendation",
                userId="user-1",
                workspaceId="ws-1",
                question="Chủ nhà có được tăng giá thuê không?",
            )
        )

        self.assertEqual(response.responseStatus, "OUT_OF_KNOWLEDGE_BASE")
        self.assertEqual(response.citations, [])
        self.assertFalse(response.shouldSuggestTicket)

    def test_ungrounded_recommendation_bullet_is_removed(self) -> None:
        service = RagQueryService(retrieval_service=_FakeRetrievalService(), llm_client=_FakeLlmClient())
        answer = (
            "**Khuyến nghị:**\n"
            "* Kiểm tra thỏa thuận về giá thuê [KB-1].\n"
            "* Thực hiện một hành động khác không có căn cứ."
        )

        filtered, removed = service._filter_ungrounded_recommendation_items(answer)

        self.assertEqual(removed, 1)
        self.assertIn("[KB-1]", filtered)
        self.assertNotIn("không có căn cứ", filtered)
        self.assertTrue(service._recommendation_section_is_grounded(filtered))

    def test_legal_search_query_uses_recent_user_history_and_bounded_document_excerpt(self) -> None:
        hits = _FakeRetrievalService().search_user_chunks(
            "đặt cọc",
            user_id="user-1",
            workspace_id="ws-1",
            top_k=5,
        )
        history = (
            "USER: Tôi là bên B trong hợp đồng thuê nhà.\n\n"
            "ASSISTANT: Một câu trả lời cũ không nên đi vào retrieval query."
        )

        query = build_legal_search_query("đặt cọc", hits, chat_history=history)

        self.assertIn("Current question: đặt cọc", query)
        self.assertIn("Tôi là bên B trong hợp đồng thuê nhà", query)
        self.assertNotIn("câu trả lời cũ", query)
        self.assertIn("[USER-1]", query)
        self.assertLess(len(query), 5000)
        self.assertEqual(extract_recent_user_history(history), "Tôi là bên B trong hợp đồng thuê nhà.")
        legal_text_query = build_legal_text_query("đặt cọc", chat_history=history)
        self.assertIn("bảo đảm", legal_text_query)
        self.assertIn("hoàn trả tiền cọc", legal_text_query)

    def test_foreign_law_query_returns_out_of_kb(self) -> None:
        service = RagQueryService(
            retrieval_service=_FakeRetrievalService(),
            llm_client=_FakeLlmClient(),
        )
        response = service.query(
            RagQueryRequest(
                requestId="req-4",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="chat-1",
                question="Phân tích hợp đồng thuê trọ theo luật Singapore",
            )
        )

        self.assertEqual(response.intent, "FOREIGN_LAW_QUERY")
        self.assertEqual(response.responseStatus, "OUT_OF_KNOWLEDGE_BASE")
        self.assertEqual(response.suggestionType, "SUGGEST_LAWYER")
        self.assertFalse(response.shouldSuggestTicket)

    def test_unsafe_query_is_refused(self) -> None:
        service = RagQueryService(
            retrieval_service=_FakeRetrievalService(),
            llm_client=_FakeLlmClient(),
        )
        response = service.query(
            RagQueryRequest(
                requestId="req-5",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="chat-1",
                question="Giúp tôi cài điều khoản để bên kia không biết",
            )
        )

        self.assertEqual(response.intent, "UNSAFE_LEGAL_REQUEST")
        self.assertEqual(response.responseStatus, "UNSAFE_REQUEST")
        self.assertEqual(response.suggestionType, "REFUSE_AND_REDIRECT")
        self.assertFalse(response.shouldSuggestTicket)

    def test_generic_uploaded_contract_review_does_not_ask_for_more_info(self) -> None:
        service = RagQueryService(
            retrieval_service=_FakeRetrievalService(),
            llm_client=_FakeLlmClient(),
        )
        response = service.query(
            RagQueryRequest(
                requestId="req-6",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="chat-1",
                question="Phân tích hợp đồng của tôi",
            )
        )

        self.assertEqual(response.intent, "CONTRACT_RISK_ANALYSIS")
        self.assertTrue(response.inputComplete)
        self.assertNotEqual(response.userActionHint, "PROVIDE_MORE_INFO")

    def test_multiple_close_scored_laws_are_not_treated_as_conflicting(self) -> None:
        service = RagQueryService(retrieval_service=_FakeRetrievalService(), llm_client=_FakeLlmClient())
        hits = [
            RagChunkHit(
                citationId="KB-1",
                sourceType="SYSTEM_KB",
                score=0.82,
                chunkText="Quy định thứ nhất",
                lawCode="LAW-A",
            ),
            RagChunkHit(
                citationId="KB-2",
                sourceType="SYSTEM_KB",
                score=0.81,
                chunkText="Quy định thứ hai",
                lawCode="LAW-B",
            ),
            RagChunkHit(
                citationId="KB-3",
                sourceType="SYSTEM_KB",
                score=0.80,
                chunkText="Quy định thứ ba",
                lawCode="LAW-C",
            ),
        ]

        issue = service._detect_retrieval_issue(
            "Phân tích rủi ro hợp đồng",
            LegalQueryIntent.CONTRACT_RISK_ANALYSIS,
            hits,
        )

        self.assertIsNone(issue)

    def test_valid_retrieval_issue_checks_are_preserved(self) -> None:
        service = RagQueryService(retrieval_service=_FakeRetrievalService(), llm_client=_FakeLlmClient())
        low_hit = RagChunkHit(
            citationId="KB-1",
            sourceType="SYSTEM_KB",
            score=0.4,
            chunkText="Low relevance",
        )
        partial_hit = RagChunkHit(
            citationId="KB-1",
            sourceType="SYSTEM_KB",
            score=0.55,
            chunkText="Partial coverage",
        )

        self.assertEqual(
            service._detect_retrieval_issue("Phân tích", LegalQueryIntent.CLAUSE_ANALYSIS, []).intent,
            LegalQueryIntent.NO_RELEVANT_DOCUMENT_FOUND,
        )
        self.assertEqual(
            service._detect_retrieval_issue("Quy định hiện hành", LegalQueryIntent.CLAUSE_ANALYSIS, []).intent,
            LegalQueryIntent.OUTDATED_KNOWLEDGE_BASE,
        )
        self.assertEqual(
            service._detect_retrieval_issue("Phân tích", LegalQueryIntent.CLAUSE_ANALYSIS, [low_hit]).intent,
            LegalQueryIntent.LOW_RETRIEVAL_CONFIDENCE,
        )
        self.assertEqual(
            service._detect_retrieval_issue("Phân tích", LegalQueryIntent.CLAUSE_ANALYSIS, [partial_hit]).intent,
            LegalQueryIntent.PARTIAL_KB_COVERAGE,
        )

    def test_response_contains_only_citations_actually_used(self) -> None:
        llm_client = SimpleNamespace(
            generate=lambda **_: LlmResponse(
                answer="Kết luận có căn cứ [USER-1] [KB-2].",
                risk_level="MEDIUM",
                confidence_score=0.8,
            )
        )
        service = RagQueryService(retrieval_service=_TwoKbRetrievalService(), llm_client=llm_client)

        response = service.query(
            RagQueryRequest(
                requestId="req-used-only",
                userId="user-1",
                workspaceId="ws-1",
                question="Phân tích hợp đồng của tôi",
            )
        )

        self.assertEqual([citation.citationId for citation in response.citations], ["USER-1", "KB-2"])
        self.assertEqual(response.usedKnowledgeCitationIds, ["KB-2"])
        self.assertNotIn(response.responseStatus, {"LOW_CONFIDENCE", "OUT_OF_KNOWLEDGE_BASE"})

    def test_multiple_valid_kb_citations_from_different_laws_are_accepted(self) -> None:
        llm_client = SimpleNamespace(
            generate=lambda **_: LlmResponse(
                answer="Hai quy định bổ sung cho nhau [KB-1][KB-2].",
                risk_level="MEDIUM",
                confidence_score=0.82,
            )
        )
        service = RagQueryService(retrieval_service=_TwoKbRetrievalService(), llm_client=llm_client)

        response = service.query(
            RagQueryRequest(
                requestId="req-multi-kb",
                userId="user-1",
                workspaceId="ws-1",
                question="Phân tích hợp đồng của tôi",
            )
        )

        self.assertEqual(response.usedKnowledgeCitationIds, ["KB-1", "KB-2"])
        self.assertEqual([citation.citationId for citation in response.citations], ["KB-1", "KB-2"])

    def test_missing_or_user_only_kb_citation_is_not_accepted(self) -> None:
        for answer in ("Kết luận không có citation.", "Nội dung hợp đồng [USER-1]."):
            with self.subTest(answer=answer):
                llm_client = SimpleNamespace(
                    generate=lambda answer=answer, **_: LlmResponse(
                        answer=answer,
                        risk_level="HIGH",
                        confidence_score=0.9,
                    )
                )
                service = RagQueryService(retrieval_service=_FakeRetrievalService(), llm_client=llm_client)

                response = service.query(
                    RagQueryRequest(
                        requestId="req-no-kb-citation",
                        userId="user-1",
                        workspaceId="ws-1",
                        question="Phân tích hợp đồng của tôi",
                    )
                )

                self.assertEqual(response.responseStatus, "OUT_OF_KNOWLEDGE_BASE")
                self.assertEqual(response.citations, [])
                self.assertFalse(response.shouldSuggestTicket)

    def test_knowledge_retrieval_keeps_only_active_admin_system_kb(self) -> None:
        chunks = [
            RetrievedChunk(
                chunk_id="user",
                text="User document",
                score=0.9,
                title="User",
                source_type="USER_DOCUMENT",
                metadata={"source_type": "USER_DOCUMENT", "effective_status": "ACTIVE"},
            ),
            RetrievedChunk(
                chunk_id="inactive",
                text="Inactive law",
                score=0.89,
                title="Inactive",
                source_type="SYSTEM_KB",
                metadata={
                    "source_type": "SYSTEM_KB",
                    "effective_status": "INACTIVE",
                    "ingested_by_role": "ADMIN",
                },
            ),
            RetrievedChunk(
                chunk_id="admin-active",
                text="Active admin law",
                score=0.88,
                title="Active",
                source_type="SYSTEM_KB",
                metadata={
                    "source_type": "SYSTEM_KB",
                    "effective_status": "ACTIVE",
                    "visibility": "PUBLIC",
                    "active": True,
                    "ingested_by_role": "ADMIN",
                },
            ),
        ]
        repository = SimpleNamespace(search_knowledge_chunks=lambda *_, **__: chunks)
        embedding_service = SimpleNamespace(embed_text=lambda _: [0.1, 0.2])
        service = RetrievalService(repository=repository, embedding_service=embedding_service)

        hits = service.search_knowledge_chunks("query", top_k=5)

        self.assertEqual([hit.rawChunkId for hit in hits], ["admin-active"])
        self.assertEqual([hit.citationId for hit in hits], ["KB-1"])

    def test_invalid_llm_citation_is_downgraded_and_never_suggests_ticket(self) -> None:
        llm_client = SimpleNamespace(
            generate=lambda **_: LlmResponse(
                answer="Có rủi ro nghiêm trọng [KB-999].",
                risk_level="CRITICAL",
                confidence_score=0.95,
                used_knowledge_citation_ids=("KB-999",),
            )
        )
        service = RagQueryService(retrieval_service=_FakeRetrievalService(), llm_client=llm_client)

        response = service.query(
            RagQueryRequest(
                requestId="req-invalid-citation",
                userId="user-1",
                workspaceId="ws-1",
                question="Phân tích hợp đồng của tôi",
            )
        )

        self.assertEqual(response.responseStatus, "LOW_CONFIDENCE")
        self.assertLessEqual(response.confidenceScore, 0.4)
        self.assertEqual(response.citations, [])
        self.assertEqual(response.usedKnowledgeCitationIds, [])
        self.assertFalse(response.shouldSuggestTicket)
        self.assertEqual(response.userActionHint, "PROVIDE_MORE_INFO")

    def test_high_risk_grounded_legal_analysis_can_suggest_ticket(self) -> None:
        llm_client = SimpleNamespace(
            generate=lambda **_: LlmResponse(
                answer="Điều khoản có rủi ro cao [USER-1] [KB-1].",
                risk_level="HIGH",
                confidence_score=0.9,
                used_knowledge_citation_ids=("KB-1",),
                used_user_citation_ids=("USER-1",),
            )
        )
        service = RagQueryService(retrieval_service=_FakeRetrievalService(), llm_client=llm_client)

        response = service.query(
            RagQueryRequest(
                requestId="req-ticket",
                userId="user-1",
                workspaceId="ws-1",
                question="Phân tích hợp đồng của tôi",
            )
        )

        self.assertEqual(response.responseStatus, "HIGH_RISK_REQUIRE_LAWYER")
        self.assertTrue(response.shouldSuggestTicket)
        self.assertEqual(response.userActionHint, "CREATE_TICKET")

    def test_ticket_eligibility_rejects_bad_intents_and_medium_risk(self) -> None:
        service = RagQueryService(retrieval_service=_FakeRetrievalService(), llm_client=_FakeLlmClient())
        non_ticket_intents = {
            LegalQueryIntent.OUT_OF_DOMAIN_GENERAL_QUERY,
            LegalQueryIntent.INVALID_OR_MEANINGLESS_QUERY,
            LegalQueryIntent.UNDER_SPECIFIED_LEGAL_QUERY,
            LegalQueryIntent.UNKNOWN_USER_ROLE,
            LegalQueryIntent.UNKNOWN_JURISDICTION,
            LegalQueryIntent.INCOMPLETE_DOCUMENT,
            LegalQueryIntent.LEGAL_BUT_NOT_CONTRACT_SCOPE,
            LegalQueryIntent.CONTRACT_TYPE_OUT_OF_STUDENT_SCOPE,
            LegalQueryIntent.FOREIGN_LAW_QUERY,
            LegalQueryIntent.PROMPT_INJECTION_OR_POLICY_BYPASS,
            LegalQueryIntent.UNSAFE_LEGAL_REQUEST,
            LegalQueryIntent.OVERCONFIDENT_LEGAL_CONCLUSION_REQUEST,
            LegalQueryIntent.INSUFFICIENT_FACTS,
            LegalQueryIntent.NO_RELEVANT_DOCUMENT_FOUND,
            LegalQueryIntent.LOW_RETRIEVAL_CONFIDENCE,
            LegalQueryIntent.PARTIAL_KB_COVERAGE,
            LegalQueryIntent.OUTDATED_KNOWLEDGE_BASE,
            LegalQueryIntent.CONFLICTING_REFERENCES,
        }

        for intent in non_ticket_intents:
            with self.subTest(intent=intent):
                self.assertFalse(
                    service._should_suggest_ticket(
                        intent=intent,
                        input_complete=True,
                        used_knowledge_ids=["KB-1"],
                        invalid_citation_ids=[],
                        risk_level="HIGH",
                        response_status="HIGH_RISK_REQUIRE_LAWYER",
                    )
                )

        self.assertFalse(
            service._should_suggest_ticket(
                intent=LegalQueryIntent.CONTRACT_RISK_ANALYSIS,
                input_complete=True,
                used_knowledge_ids=["KB-1"],
                invalid_citation_ids=[],
                risk_level="MEDIUM",
                response_status="HIGH_RISK_REQUIRE_LAWYER",
            )
        )

    def test_signing_decision_offers_ticket_when_grounded_even_at_medium_risk(self) -> None:
        service = RagQueryService(retrieval_service=_FakeRetrievalService(), llm_client=_FakeLlmClient())

        self.assertTrue(
            service._should_suggest_ticket(
                intent=LegalQueryIntent.SIGNING_DECISION_SUPPORT,
                input_complete=True,
                used_knowledge_ids=["KB-1"],
                invalid_citation_ids=[],
                risk_level="MEDIUM",
                response_status="PARTIALLY_ANSWERABLE",
            )
        )

        self.assertTrue(
            service._should_suggest_ticket(
                intent=LegalQueryIntent.SIGNING_DECISION_SUPPORT,
                input_complete=False,
                used_knowledge_ids=[],
                invalid_citation_ids=["KB-999"],
                risk_level="LOW",
                response_status="NEED_MORE_INFORMATION",
            )
        )

        safe_answer = service._apply_signing_decision_safety("Một số điều khoản cần được làm rõ.")
        self.assertIn("không khuyến khích bạn ký", safe_answer)
        self.assertIn("tạo ticket để chuyên gia xem xét", safe_answer)

    def test_rag_query_endpoint_returns_response(self) -> None:
        fake_service = SimpleNamespace(
            query=lambda payload: RagQueryResponse(
                requestId=payload.requestId,
                chatSessionId=payload.chatSessionId,
                answer="OK",
                riskLevel="LOW",
                citations=[],
                retrievedUserChunks=0,
                retrievedKnowledgeChunks=0,
            )
        )
        with patch("app.api.rag_api.get_rag_query_service", return_value=fake_service):
            client = TestClient(app)
            response = client.post(
                "/internal/rag/query",
                json={
                    "requestId": "req-1",
                    "userId": "user-1",
                    "workspaceId": "ws-1",
                    "chatSessionId": "chat-1",
                    "question": "Hợp đồng có rủi ro gì?",
                    "topKUserChunks": 5,
                    "topKKnowledgeChunks": 5,
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["answer"], "OK")
        self.assertEqual(response.json()["riskLevel"], "LOW")

    def test_rag_query_endpoint_returns_bad_gateway_when_llm_fails(self) -> None:
        def fail_query(payload):
            raise LlmGenerationError("Gemini request failed")

        with patch(
            "app.api.rag_api.get_rag_query_service",
            return_value=SimpleNamespace(query=fail_query),
        ):
            response = TestClient(app).post(
                "/internal/rag/query",
                json={
                    "requestId": "req-failed",
                    "userId": "user-1",
                    "workspaceId": "ws-1",
                    "question": "Phân tích hợp đồng thuê nhà",
                },
            )

        self.assertEqual(response.status_code, 502)
        self.assertIn("Gemini request failed", response.json()["detail"])

    def test_rag_preview_endpoint_returns_chunks(self) -> None:
        fake_service = SimpleNamespace(
            preview=lambda payload: SimpleNamespace(
                requestId=payload.requestId,
                chatSessionId=payload.chatSessionId,
                question=payload.question,
                legalSearchQuery=f"{payload.question}\n\nRelevant user contract context:\n[USER-1] demo",
                userChunks=[],
                knowledgeChunks=[],
                retrievedUserChunks=0,
                retrievedKnowledgeChunks=0,
            )
        )
        with patch("app.api.rag_api.get_rag_query_service", return_value=fake_service):
            client = TestClient(app)
            response = client.post(
                "/internal/rag/preview",
                json={
                    "requestId": "req-1",
                    "userId": "user-1",
                    "workspaceId": "ws-1",
                    "chatSessionId": "chat-1",
                    "question": "Hợp đồng có rủi ro gì?",
                    "topKUserChunks": 5,
                    "topKKnowledgeChunks": 5,
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertIn("legalSearchQuery", response.json())


if __name__ == "__main__":
    unittest.main()
