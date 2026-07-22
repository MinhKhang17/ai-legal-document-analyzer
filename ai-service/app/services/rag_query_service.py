from __future__ import annotations

import logging
import re
import unicodedata
from pathlib import Path

from app.core.knowledge_access import is_published_system_kb
from app.core.config import settings
from app.models.intent_enums import (
    ContractType,
    LegalQueryIntent,
    ResponseStatus,
    RiskLevel,
    SuggestionType,
)
from app.schemas import (
    ConversationMemoryUpdate,
    RagCitation,
    RagPreviewChunk,
    RagPreviewResponse,
    RagQueryRequest,
    RagQueryResponse,
    RagUsage,
    TokenUsageBreakdown,
)
from app.services.completeness_checker import check_completeness
from app.services.llm_intent_detector import detect_intent_smart
from app.services.conversation_memory_service import ConversationMemoryService, PreparedConversationMemory
from app.services.conversation_context import (
    LegalConversationContextStore,
    build_analysis_snapshot,
    is_follow_up_query,
    merge_snapshot_with_current_hits,
)
from app.services.intent_detector import detect_intent
from app.services.llm_client import RagLlmClient, build_default_llm_client, sanitize_response
from app.services.prompt_builder import build_intent_instruction, build_system_prompt, build_user_prompt
from app.services.query_builder import (
    build_knowledge_context,
    build_legal_search_query,
    build_legal_text_query,
    build_user_context,
    extract_recent_user_history,
)
from app.services.retrieval_service import RagChunkHit, RetrievalService
from app.services.token_budget import PromptTokenBudget, budget_for_intent, estimate_tokens, truncate_to_token_budget


logger = logging.getLogger(__name__)

_CITATION_PATTERN = re.compile(r"\[((?:KB|USER)-\d+)\]", re.IGNORECASE)
_SINGULAR_DOCUMENT_REFERENCE = re.compile(
    r"\b(?:tài liệu|hợp đồng|văn bản)\s+(?:này|đó|trên)\b|\bthis\s+(?:document|contract)\b",
    re.IGNORECASE,
)
_LEGAL_SUMMARY_MARKERS = (
    "đánh giá", "rủi ro", "pháp lý", "hợp pháp", "hiệu lực", "vô hiệu",
    "căn cứ pháp luật", "quy định pháp luật", "vi phạm",
    "legal risk", "legality", "validity",
)
_TICKET_ELIGIBLE_INTENTS = frozenset(
    {
        LegalQueryIntent.STUDENT_RENTAL_CONTRACT_REVIEW,
        LegalQueryIntent.PART_TIME_OR_INTERNSHIP_CONTRACT_REVIEW,
        LegalQueryIntent.SMALL_SERVICE_CONTRACT_REVIEW,
        LegalQueryIntent.SMALL_SALE_CONTRACT_REVIEW,
        LegalQueryIntent.PERSONAL_LOAN_NOTE_REVIEW,
        LegalQueryIntent.CONTRACT_RISK_ANALYSIS,
        LegalQueryIntent.CLAUSE_ANALYSIS,
        LegalQueryIntent.MISSING_CLAUSE_CHECK,
        LegalQueryIntent.SIGNING_DECISION_SUPPORT,
    }
)
_LEGAL_GROUNDED_INTENTS = _TICKET_ELIGIBLE_INTENTS | frozenset(
    {
        LegalQueryIntent.LEGAL_KB_QUESTION,
        LegalQueryIntent.CLAUSE_REVISION,
        LegalQueryIntent.CLAUSE_DRAFTING,
    }
)


class LlmGenerationError(RuntimeError):
    """Raised when retrieval succeeds but the configured LLM cannot produce an answer."""


class RagQueryService:
    def __init__(
        self,
        *,
        retrieval_service: RetrievalService | None = None,
        llm_client: RagLlmClient | None = None,
        context_store: LegalConversationContextStore | None = None,
        conversation_memory_service: ConversationMemoryService | None = None,
        llm_enabled: bool | None = None,
    ) -> None:
        self.retrieval_service = retrieval_service or RetrievalService()
        self.llm_client = llm_client or build_default_llm_client()
        self.context_store = context_store or LegalConversationContextStore()
        self.conversation_memory_service = conversation_memory_service
        self.llm_enabled = (
            settings.llm_query_enabled
            if llm_enabled is None and llm_client is None
            else (True if llm_enabled is None else llm_enabled)
        )

    def _get_conversation_memory_service(self) -> ConversationMemoryService:
        if self.conversation_memory_service is None:
            embedding_service = getattr(self.retrieval_service, "embedding_service", None)
            self.conversation_memory_service = ConversationMemoryService(embedding_service=embedding_service)
        return self.conversation_memory_service

    def _request_history_text(self, request: RagQueryRequest) -> str | None:
        if request.recentHistory:
            return "\n\n".join(
                f"{message.role.upper()}: {message.content}" for message in request.recentHistory
            )
        return request.chatHistory

    def query(self, request: RagQueryRequest) -> RagQueryResponse:
        # ── Step 0a: Export chat to DOCX shortcut ──
        from app.services.contract_generation_service import (
            is_contract_generation_intent,
            is_export_docx_intent,
        )
        from app.services.safe_query_shortcuts import (
            build_contract_prompt_response,
            build_conversation_shortcut,
        )

        shortcut = build_conversation_shortcut(request)
        if shortcut is not None:
            return shortcut

        follow_up = is_follow_up_query(request.question)
        previous_snapshot = self.context_store.latest(request.chatSessionId) if follow_up else None
        bounded_history_text = self._request_history_text(request)

        if is_export_docx_intent(request.question):
            return build_contract_prompt_response(request)

        # ── Step 0b: Contract generation shortcut ──
        if is_contract_generation_intent(request.question) and not follow_up:
            return build_contract_prompt_response(request)

        user_hits, legal_search_query, knowledge_hits = self._retrieve(request)
        logger.info("DEBUG RAG QUERY: request=%s, user_hits_count=%d", request.model_dump(exclude={"chatHistory", "conversationSummaryJson"}), len(user_hits))
        if self._needs_document_selection(request, user_hits):
            return self._build_document_selection_response(request, user_hits, knowledge_hits)
        if follow_up and previous_snapshot is None and not bounded_history_text.strip():
            return self._build_missing_snapshot_response(request, user_hits, knowledge_hits)

        # ── Step 2: Detect intent ──
        intent_result = detect_intent_smart(
            request.question,
            has_user_chunks=bool(user_hits),
            has_knowledge_chunks=bool(knowledge_hits),
            conversation_context=extract_recent_user_history(bounded_history_text),
        )
        completeness = check_completeness(
            intent_result.intent,
            contract_type=intent_result.contract_type,
            user_role=intent_result.user_role,
            has_user_chunks=bool(user_hits),
            question=request.question,
            conversation_context=extract_recent_user_history(bounded_history_text),
        )

        if self._should_short_circuit(intent_result.intent) and (
            not follow_up or self._must_guard_follow_up(intent_result.intent)
        ):
            return self._build_guard_response(
                request=request,
                intent_result=intent_result,
                completeness=completeness,
                user_hits=user_hits,
                knowledge_hits=knowledge_hits,
            )

        if not completeness.is_complete and not follow_up:
            adjusted_intent_result = self._adjust_for_completeness(intent_result, completeness)
            return self._build_guard_response(
                request=request,
                intent_result=adjusted_intent_result,
                completeness=completeness,
                user_hits=user_hits,
                knowledge_hits=knowledge_hits,
            )

        contract_summary_only = (
            intent_result.intent == LegalQueryIntent.CONTRACT_SUMMARY
            and not self._summary_requires_legal_kb(request.question)
        )
        retrieval_issue = None if follow_up else self._detect_retrieval_issue(
            request.question,
            intent_result.intent,
            knowledge_hits,
        )
        if retrieval_issue is not None:
            response = self._build_guard_response(
                request=request,
                intent_result=retrieval_issue,
                completeness=completeness,
                user_hits=user_hits,
                knowledge_hits=knowledge_hits,
            )
            if intent_result.intent == LegalQueryIntent.SIGNING_DECISION_SUPPORT:
                response = response.model_copy(update={
                    "answer": self._apply_signing_decision_safety(response.answer),
                    "shouldSuggestTicket": True,
                    "suggestionType": SuggestionType.SUGGEST_LAWYER.value,
                    "suggestionReason": self._default_suggestion_reason(
                        SuggestionType.SUGGEST_LAWYER.value
                    ),
                    "userActionHint": "CREATE_TICKET",
                })
            return response

        available_user_docs, available_system_docs = self._fetch_available_docs(request)
        prompt_user_hits = user_hits
        prompt_knowledge_hits = knowledge_hits
        if follow_up and previous_snapshot is not None:
            if request.attachedDocumentIds is not None:
                allowed_snapshot_document_ids = set(request.attachedDocumentIds)
            elif request.documentId:
                allowed_snapshot_document_ids = {request.documentId}
            else:
                allowed_snapshot_document_ids = None
            prompt_user_hits, prompt_knowledge_hits = merge_snapshot_with_current_hits(
                previous_snapshot,
                user_hits,
                knowledge_hits,
                allowed_document_ids=allowed_snapshot_document_ids,
            )
        system_prompt = build_system_prompt()
        try:
            prepared_memory = self._get_conversation_memory_service().prepare(
                request,
                llm_client=self.llm_client,
                llm_enabled=self.llm_enabled,
            )
        except Exception as exc:
            logger.warning(
                "Conversation memory preparation failed for session %s: %s",
                request.chatSessionId,
                type(exc).__name__,
            )
            prepared_memory = PreparedConversationMemory(
                summary_json=request.conversationSummaryJson or "[none]",
                recent_history=bounded_history_text or "[none]",
                relevant_history="[none]",
                memory_update=None,
            )
        budget_intent = (
            LegalQueryIntent.CONTRACT_RISK_ANALYSIS
            if intent_result.intent == LegalQueryIntent.CONTRACT_SUMMARY and not contract_summary_only
            else intent_result.intent
        )
        token_budget = budget_for_intent(budget_intent)
        system_prompt = truncate_to_token_budget(system_prompt, token_budget.system_prompt)
        user_prompt = build_user_prompt(
            request.question,
            prompt_user_hits,
            prompt_knowledge_hits,
            chat_history=None,
            available_user_docs=available_user_docs,
            available_system_docs=available_system_docs,
            workspace_id=request.workspaceId,
            analysis_snapshot=previous_snapshot,
            is_follow_up=follow_up,
            session_active_document_ids=request.attachedDocumentIds,
            message_attached_document_ids=request.messageAttachedDocumentIds,
            focused_document_id=request.focusedDocumentId or request.documentId,
            contract_summary_only=contract_summary_only,
            conversation_summary=prepared_memory.summary_json,
            recent_history=prepared_memory.recent_history,
            relevant_history=prepared_memory.relevant_history,
            token_budget=token_budget,
        )
        user_prompt += build_intent_instruction(
            intent_result.intent,
            contract_type=intent_result.contract_type,
            response_mode=intent_result.response_mode,
            completeness_questions=None,
        )
        token_breakdown = self._build_token_breakdown(
            system_prompt=system_prompt,
            prepared_memory=prepared_memory,
            user_hits=prompt_user_hits,
            knowledge_hits=prompt_knowledge_hits,
            budget=token_budget,
        )

        if not self.llm_enabled:
            logger.info(
                "Prompt token breakdown request=%s intent=%s system=%s summary=%s recent=%s relevant=%s user_docs=%s legal_kb=%s output=0",
                request.requestId,
                intent_result.intent.value,
                token_breakdown.systemPrompt,
                token_breakdown.conversationSummary,
                token_breakdown.recentHistory,
                token_breakdown.relevantHistory,
                token_breakdown.userDocumentContext,
                token_breakdown.legalKbContext,
            )
            logger.info("LLM_QUERY_ENABLED=false; returning prompt preview for request %s", request.requestId)
            return self._build_prompt_preview_response(
                request=request,
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                intent_result=intent_result,
                completeness=completeness,
                user_hits=prompt_user_hits,
                knowledge_hits=prompt_knowledge_hits,
                memory_update=prepared_memory.memory_update,
                token_breakdown=token_breakdown,
            )

        llm_result = self.llm_client.generate(system_prompt=system_prompt, user_prompt=user_prompt)
        if llm_result.error or not llm_result.answer:
            raise LlmGenerationError(llm_result.error or "LLM returned an empty answer")
        raw_answer = sanitize_response(llm_result.answer or self._build_fallback_answer(user_hits, knowledge_hits))
        logger.info("DEBUG RAW ANSWER:\n%s", raw_answer)
        answer, removed_recommendations = self._filter_ungrounded_recommendation_items(raw_answer)
        logger.info("DEBUG FILTERED ANSWER:\n%s", answer)
        if removed_recommendations:
            logger.warning(
                "Removed %s ungrounded recommendation item(s) for request %s",
                removed_recommendations,
                request.requestId,
            )
        used_knowledge_ids, used_user_ids, invalid_citation_ids = self._validate_used_citations(
            answer=answer,
            declared_knowledge_ids=llm_result.used_knowledge_citation_ids,
            declared_user_ids=llm_result.used_user_citation_ids,
            user_hits=prompt_user_hits,
            knowledge_hits=prompt_knowledge_hits,
        )
        used_ids = {*used_knowledge_ids, *used_user_ids}
        citations = [
            self._to_citation(hit)
            for hit in [*prompt_user_hits, *prompt_knowledge_hits]
            if hit.citationId in used_ids
        ]
        recommendations_grounded = self._recommendation_section_is_grounded(answer)
        requires_legal_grounding = (
            intent_result.intent in _LEGAL_GROUNDED_INTENTS
            or (intent_result.intent == LegalQueryIntent.CONTRACT_SUMMARY and not contract_summary_only)
        )
        grounding_failed = bool(invalid_citation_ids) or (
            requires_legal_grounding and not used_knowledge_ids
        ) or (requires_legal_grounding and not recommendations_grounded)
        if grounding_failed:
            logger.warning(
                "Grounding validation failed for request %s: used_kb=%s invalid=%s recommendations_grounded=%s",
                request.requestId,
                used_knowledge_ids,
                invalid_citation_ids,
                recommendations_grounded,
            )
            answer = self._grounding_failure_answer(bool(invalid_citation_ids))
            citations = []
            used_knowledge_ids = []
            used_user_ids = []

        if intent_result.intent == LegalQueryIntent.SIGNING_DECISION_SUPPORT:
            answer = self._apply_signing_decision_safety(answer)

        effective_confidence = llm_result.confidence_score
        retrieved_citations = [
            self._to_citation(hit) for hit in [*prompt_user_hits, *prompt_knowledge_hits]
        ]
        if effective_confidence is None and retrieved_citations:
            retrieval_confidence = sum(citation.score for citation in retrieved_citations) / len(retrieved_citations)
            effective_confidence = round(min(intent_result.confidence, retrieval_confidence), 4)
        if grounding_failed:
            effective_confidence = min(effective_confidence if effective_confidence is not None else 0.4, 0.4)
        risk_level = (
            llm_result.risk_level
            if llm_result.risk_level in {"NONE", "LOW", "MEDIUM", "HIGH", "CRITICAL"}
            else intent_result.risk_level.value
        )

        if grounding_failed:
            response_status = (
                ResponseStatus.LOW_CONFIDENCE.value
                if invalid_citation_ids
                else ResponseStatus.OUT_OF_KNOWLEDGE_BASE.value
            )
            suggestion_type = SuggestionType.ASK_MORE_FACTS.value
        else:
            response_status = self._postprocess_response_status(
                base_status=intent_result.response_status,
                confidence_score=effective_confidence,
                citations=citations,
                risk_level=risk_level,
            )
            suggestion_type = llm_result.suggestion_type or intent_result.suggestion_type.value
            if (
                response_status == ResponseStatus.HIGH_RISK_REQUIRE_LAWYER.value
                and suggestion_type in {"NONE", SuggestionType.DIRECT_ANSWER.value}
            ):
                suggestion_type = SuggestionType.SUGGEST_LAWYER.value
        user_action_hint = self._map_user_action_hint(suggestion_type, response_status)
        should_suggest_ticket = self._should_suggest_ticket(
            intent=intent_result.intent,
            input_complete=completeness.is_complete,
            used_knowledge_ids=used_knowledge_ids,
            invalid_citation_ids=invalid_citation_ids,
            risk_level=risk_level,
            response_status=response_status,
        )
        if should_suggest_ticket:
            if intent_result.intent == LegalQueryIntent.SIGNING_DECISION_SUPPORT:
                suggestion_type = SuggestionType.SUGGEST_LAWYER.value
            user_action_hint = "CREATE_TICKET"

        response = RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=self._enrich_answer_with_download_links(answer, request.workspaceId, prompt_knowledge_hits),
            confidenceScore=effective_confidence,
            shouldSuggestTicket=should_suggest_ticket,
            suggestionType=suggestion_type,
            suggestionReason=llm_result.suggestion_reason or self._default_suggestion_reason(suggestion_type),
            missingInformation=llm_result.missing_information,
            riskLevel=risk_level,
            legalDomain=llm_result.legal_domain or self._default_legal_domain(intent_result.contract_type),
            userActionHint=user_action_hint,
            citations=citations,
            usedKnowledgeCitationIds=used_knowledge_ids,
            usedUserCitationIds=used_user_ids,
            retrievedUserChunks=len(user_hits),
            retrievedKnowledgeChunks=len(knowledge_hits),
            intent=intent_result.intent.value,
            intents=self._ordered_intents(request.question, intent_result.intent.value),
            contractType=intent_result.contract_type.value,
            userRole=intent_result.user_role.value,
            jurisdiction=intent_result.jurisdiction.value,
            responseStatus=response_status,
            responseMode=intent_result.response_mode.value,
            inputComplete=completeness.is_complete,
            missingInputs=completeness.missing_items or None,
            analysis=llm_result.analysis,
            model=llm_result.model,
            usage=RagUsage(
                promptTokens=llm_result.prompt_tokens,
                completionTokens=llm_result.completion_tokens,
                totalTokens=llm_result.total_tokens,
            ),
            llmExecuted=True,
            conversationMemoryUpdate=prepared_memory.memory_update,
            tokenUsageBreakdown=token_breakdown.model_copy(update={"output": llm_result.completion_tokens}),
        )
        self._get_conversation_memory_service().index_completed_turn(
            request,
            answer,
            [*used_user_ids, *used_knowledge_ids],
        )
        final_breakdown = response.tokenUsageBreakdown
        if final_breakdown is not None:
            logger.info(
                "Prompt token breakdown request=%s intent=%s system=%s summary=%s recent=%s relevant=%s user_docs=%s legal_kb=%s output=%s",
                request.requestId,
                intent_result.intent.value,
                final_breakdown.systemPrompt,
                final_breakdown.conversationSummary,
                final_breakdown.recentHistory,
                final_breakdown.relevantHistory,
                final_breakdown.userDocumentContext,
                final_breakdown.legalKbContext,
                final_breakdown.output,
            )
        if request.chatSessionId:
            self.context_store.append(
                build_analysis_snapshot(
                    session_id=request.chatSessionId,
                    analysis_id=request.requestId,
                    contract_type=response.contractType or ContractType.UNKNOWN.value,
                    user_role=response.userRole or "UNKNOWN",
                    jurisdiction=response.jurisdiction or "UNKNOWN",
                    risk_level=response.riskLevel,
                    confidence=response.confidenceScore,
                    response_status=response.responseStatus or ResponseStatus.NEED_MORE_INFORMATION.value,
                    suggestion_type=response.suggestionType,
                    answer=response.answer,
                    analysis=llm_result.analysis,
                    user_hits=prompt_user_hits,
                    knowledge_hits=prompt_knowledge_hits,
                    used_user_ids=response.usedUserCitationIds,
                    used_knowledge_ids=response.usedKnowledgeCitationIds,
                )
            )
        return response

    @staticmethod
    def _ordered_intents(question: str, primary_intent: str) -> list[str]:
        """Keep execution order for common compound requests without replacing the existing classifier."""
        normalized = question.lower()
        analysis_words = ("phân tích", "đánh giá", "analyze", "review")
        rewrite_words = ("viết lại", "sửa lại", "rewrite", "revise")
        intents = [primary_intent]
        if any(word in normalized for word in analysis_words) and any(word in normalized for word in rewrite_words):
            if primary_intent not in {"CLAUSE_ANALYSIS", "DOCUMENT_ANALYSIS"}:
                intents.insert(0, "CLAUSE_ANALYSIS")
            if "CLAUSE_REWRITE" not in intents:
                intents.append("CLAUSE_REWRITE")
        return intents

    def _build_token_breakdown(
        self,
        *,
        system_prompt: str,
        prepared_memory: PreparedConversationMemory,
        user_hits: list[RagChunkHit],
        knowledge_hits: list[RagChunkHit],
        budget: PromptTokenBudget,
    ) -> TokenUsageBreakdown:
        return TokenUsageBreakdown(
            systemPrompt=min(estimate_tokens(system_prompt), budget.system_prompt),
            conversationSummary=min(
                estimate_tokens(prepared_memory.summary_json), budget.conversation_summary),
            recentHistory=min(estimate_tokens(prepared_memory.recent_history), budget.recent_history),
            relevantHistory=min(estimate_tokens(prepared_memory.relevant_history), budget.relevant_history),
            userDocumentContext=min(
                estimate_tokens(build_user_context(user_hits)), budget.user_document_context),
            legalKbContext=min(
                estimate_tokens(build_knowledge_context(knowledge_hits)), budget.legal_kb_context),
            output=0,
        )

    def _build_prompt_preview_response(
        self,
        *,
        request: RagQueryRequest,
        system_prompt: str,
        user_prompt: str,
        intent_result,
        completeness,
        user_hits: list[RagChunkHit],
        knowledge_hits: list[RagChunkHit],
        memory_update: ConversationMemoryUpdate | None = None,
        token_breakdown: TokenUsageBreakdown | None = None,
    ) -> RagQueryResponse:
        answer = (
            "[LLM PROMPT PREVIEW - PROMPT CHUA DUOC GUI TOI LLM]\n\n"
            "===== SYSTEM PROMPT =====\n"
            f"{system_prompt}\n\n"
            "===== USER PROMPT =====\n"
            f"{user_prompt}"
        )
        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=answer,
            confidenceScore=None,
            shouldSuggestTicket=False,
            suggestionType="NONE",
            suggestionReason="LLM preview mode is enabled.",
            missingInformation=None,
            riskLevel=intent_result.risk_level.value,
            legalDomain=self._default_legal_domain(intent_result.contract_type),
            userActionHint="CONTINUE_CHAT",
            citations=[self._to_citation(hit) for hit in [*user_hits, *knowledge_hits]],
            retrievedUserChunks=len(user_hits),
            retrievedKnowledgeChunks=len(knowledge_hits),
            intent=intent_result.intent.value,
            contractType=intent_result.contract_type.value,
            userRole=intent_result.user_role.value,
            jurisdiction=intent_result.jurisdiction.value,
            responseStatus="PROMPT_PREVIEW",
            responseMode=intent_result.response_mode.value,
            inputComplete=completeness.is_complete,
            missingInputs=completeness.missing_items or None,
            model="prompt-preview",
            usage=RagUsage(promptTokens=0, completionTokens=0, totalTokens=0),
            llmExecuted=False,
            systemPromptPreview=system_prompt,
            userPromptPreview=user_prompt,
            conversationMemoryUpdate=memory_update,
            tokenUsageBreakdown=token_breakdown,
        )

    def preview(self, request: RagQueryRequest) -> RagPreviewResponse:
        user_hits, legal_search_query, knowledge_hits = self._retrieve(request)
        return RagPreviewResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            question=request.question,
            legalSearchQuery=legal_search_query,
            userChunks=[self._to_preview_chunk(hit) for hit in user_hits],
            knowledgeChunks=[self._to_preview_chunk(hit) for hit in knowledge_hits],
            retrievedUserChunks=len(user_hits),
            retrievedKnowledgeChunks=len(knowledge_hits),
        )

    def _retrieve(self, request: RagQueryRequest) -> tuple[list[RagChunkHit], str, list[RagChunkHit]]:
        # [] is an explicit KB-only session. None retains legacy workspace and
        # single-document request behavior for backward compatibility.
        if request.messageAttachedDocumentIds:
            effective_document_ids = request.messageAttachedDocumentIds
        elif request.focusedDocumentId:
            effective_document_ids = [request.focusedDocumentId]
        elif request.attachedDocumentIds is not None:
            effective_document_ids = request.attachedDocumentIds
        elif request.documentId:
            effective_document_ids = [request.documentId]
        else:
            effective_document_ids = None

        if effective_document_ids == []:
            user_hits = []
        else:
            search_kwargs = {
                "user_id": request.userId,
                "workspace_id": request.workspaceId,
                "top_k": request.topKUserChunks,
            }
            if effective_document_ids:
                search_kwargs["document_ids"] = effective_document_ids
            user_hits = self.retrieval_service.search_user_chunks(request.question, **search_kwargs)
        bounded_history_text = self._request_history_text(request)
        legal_search_query = build_legal_search_query(
            request.question,
            user_hits,
            chat_history=bounded_history_text,
        )
        preliminary_intent = detect_intent(
            request.question,
            has_user_chunks=bool(user_hits),
            has_knowledge_chunks=False,
            conversation_context=extract_recent_user_history(bounded_history_text),
        ).intent
        if (
            preliminary_intent == LegalQueryIntent.CONTRACT_SUMMARY
            and not self._summary_requires_legal_kb(request.question)
        ):
            knowledge_hits = []
        else:
            knowledge_hits = self.retrieval_service.search_knowledge_chunks(
                legal_search_query,
                top_k=request.topKKnowledgeChunks,
                query_text=build_legal_text_query(request.question, chat_history=bounded_history_text),
            )
        logger.info("DEBUG RAG RETRIEVAL: question=%s, preliminary_intent=%s, legal_search_query=%s, knowledge_hits_count=%d", request.question, preliminary_intent, legal_search_query, len(knowledge_hits))
        return user_hits, legal_search_query, knowledge_hits

    def _summary_requires_legal_kb(self, question: str) -> bool:
        normalized = unicodedata.normalize("NFC", question).casefold()
        return any(marker in normalized for marker in _LEGAL_SUMMARY_MARKERS)

    def _needs_document_selection(self, request: RagQueryRequest, user_hits: list[RagChunkHit]) -> bool:
        if (request.documentId or request.focusedDocumentId or request.messageAttachedDocumentIds
                or not request.attachedDocumentIds or len(set(request.attachedDocumentIds)) < 2):
            return False
        if not _SINGULAR_DOCUMENT_REFERENCE.search(request.question):
            return False

        normalized_question = unicodedata.normalize("NFC", request.question).casefold()
        for hit in user_hits:
            if not hit.fileName:
                continue
            normalized_name = unicodedata.normalize("NFC", Path(hit.fileName).stem).casefold().strip()
            if len(normalized_name) >= 4 and normalized_name in normalized_question:
                return False
        return True

    def _build_document_selection_response(
        self,
        request: RagQueryRequest,
        user_hits: list[RagChunkHit],
        knowledge_hits: list[RagChunkHit],
    ) -> RagQueryResponse:
        document_names = list(dict.fromkeys(hit.fileName for hit in user_hits if hit.fileName))
        choices = ""
        if document_names:
            choices = " Các tài liệu liên quan: " + "; ".join(document_names) + "."
        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=(
                "Phiên chat đang có nhiều tài liệu nên mình chưa xác định được “tài liệu này” hoặc "
                "“hợp đồng này” là tài liệu nào."
                f"{choices} Vui lòng chọn hoặc gọi đúng tên tài liệu cần hỏi."
            ),
            confidenceScore=1.0,
            shouldSuggestTicket=False,
            suggestionType=SuggestionType.ASK_MORE_FACTS.value,
            suggestionReason="Cần xác định đúng tài liệu đích trước khi phân tích.",
            missingInformation="Tài liệu cụ thể cần sử dụng cho câu hỏi hiện tại.",
            riskLevel=RiskLevel.UNKNOWN.value,
            legalDomain=None,
            userActionHint="PROVIDE_MORE_INFO",
            citations=[],
            usedKnowledgeCitationIds=[],
            usedUserCitationIds=[],
            retrievedUserChunks=len(user_hits),
            retrievedKnowledgeChunks=len(knowledge_hits),
            intent=LegalQueryIntent.INSUFFICIENT_FACTS.value,
            contractType=ContractType.UNKNOWN.value,
            userRole="UNKNOWN",
            jurisdiction="VIETNAM",
            responseStatus=ResponseStatus.NEED_MORE_INFORMATION.value,
            responseMode="ASK_FOR_INFORMATION",
            inputComplete=False,
            missingInputs=["targetDocument"],
            llmExecuted=False,
        )

    def _should_short_circuit(self, intent: LegalQueryIntent) -> bool:
        return intent in {
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
        }

    def _must_guard_follow_up(self, intent: LegalQueryIntent) -> bool:
        return intent in {
            LegalQueryIntent.OUT_OF_DOMAIN_GENERAL_QUERY,
            LegalQueryIntent.LEGAL_BUT_NOT_CONTRACT_SCOPE,
            LegalQueryIntent.CONTRACT_TYPE_OUT_OF_STUDENT_SCOPE,
            LegalQueryIntent.FOREIGN_LAW_QUERY,
            LegalQueryIntent.PROMPT_INJECTION_OR_POLICY_BYPASS,
            LegalQueryIntent.UNSAFE_LEGAL_REQUEST,
        }

    def _build_missing_snapshot_response(
        self,
        request: RagQueryRequest,
        user_hits: list[RagChunkHit],
        knowledge_hits: list[RagChunkHit],
    ) -> RagQueryResponse:
        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=(
                "Mình chưa có bản phân tích trước trong session này để trả lời câu hỏi follow-up. "
                "Bạn hãy gửi lại hợp đồng hoặc nội dung câu trả lời trước."
            ),
            confidenceScore=0.0,
            shouldSuggestTicket=False,
            suggestionType=SuggestionType.ASK_MORE_FACTS.value,
            suggestionReason="Session hiện tại chưa có analysis snapshot.",
            missingInformation="Hợp đồng hoặc nội dung phân tích trước trong cùng session.",
            riskLevel=RiskLevel.UNKNOWN.value,
            legalDomain=None,
            userActionHint="PROVIDE_MORE_INFO",
            citations=[],
            usedKnowledgeCitationIds=[],
            usedUserCitationIds=[],
            retrievedUserChunks=len(user_hits),
            retrievedKnowledgeChunks=len(knowledge_hits),
            intent=LegalQueryIntent.INSUFFICIENT_FACTS.value,
            contractType=ContractType.UNKNOWN.value,
            userRole="UNKNOWN",
            jurisdiction="UNKNOWN",
            responseStatus=ResponseStatus.NEED_MORE_INFORMATION.value,
            responseMode="ASK_FOR_INFORMATION",
            inputComplete=False,
            missingInputs=["analysisSnapshot"],
        )

    def _adjust_for_completeness(self, intent_result, completeness):
        missing = set(completeness.missing_items)
        suggestion_type = SuggestionType.ASK_MORE_FACTS
        if "contractDocument" in missing:
            suggestion_type = SuggestionType.ASK_UPLOAD_CONTRACT
        elif "contractType" in missing:
            suggestion_type = SuggestionType.ASK_CONTRACT_TYPE
        elif "userRole" in missing:
            suggestion_type = SuggestionType.ASK_USER_ROLE
        elif "targetClause" in missing:
            suggestion_type = SuggestionType.ASK_TARGET_CLAUSE

        return intent_result.__class__(
            intent=intent_result.intent,
            contract_type=intent_result.contract_type,
            user_role=intent_result.user_role,
            jurisdiction=intent_result.jurisdiction,
            response_mode=intent_result.response_mode,
            response_status=ResponseStatus.INCOMPLETE_INPUT,
            suggestion_type=suggestion_type,
            risk_level=intent_result.risk_level,
            confidence=intent_result.confidence,
            has_document_context=intent_result.has_document_context,
        )

    def _detect_retrieval_issue(self, question: str, intent: LegalQueryIntent, knowledge_hits: list[RagChunkHit]):
        review_like = {
            LegalQueryIntent.STUDENT_RENTAL_CONTRACT_REVIEW,
            LegalQueryIntent.PART_TIME_OR_INTERNSHIP_CONTRACT_REVIEW,
            LegalQueryIntent.SMALL_SERVICE_CONTRACT_REVIEW,
            LegalQueryIntent.SMALL_SALE_CONTRACT_REVIEW,
            LegalQueryIntent.PERSONAL_LOAN_NOTE_REVIEW,
            LegalQueryIntent.CONTRACT_RISK_ANALYSIS,
            LegalQueryIntent.CLAUSE_ANALYSIS,
            LegalQueryIntent.MISSING_CLAUSE_CHECK,
            LegalQueryIntent.SIGNING_DECISION_SUPPORT,
        }
        lowered = question.lower()
        if intent == LegalQueryIntent.CONTRACT_SUMMARY and not self._summary_requires_legal_kb(question):
            return None
        if not knowledge_hits:
            issue_intent = LegalQueryIntent.OUTDATED_KNOWLEDGE_BASE if any(word in lowered for word in ["mới nhất", "hiện hành", "cập nhật"]) else LegalQueryIntent.NO_RELEVANT_DOCUMENT_FOUND
            return self._make_retrieval_issue(issue_intent)

        avg_score = sum(hit.score for hit in knowledge_hits) / len(knowledge_hits)
        if avg_score < 0.45:
            return self._make_retrieval_issue(LegalQueryIntent.LOW_RETRIEVAL_CONFIDENCE)

        if intent in review_like and len(knowledge_hits) < 2 and avg_score < 0.6:
            return self._make_retrieval_issue(LegalQueryIntent.PARTIAL_KB_COVERAGE)

        return None

    def _make_retrieval_issue(self, issue_intent: LegalQueryIntent):
        mapping = {
            LegalQueryIntent.NO_RELEVANT_DOCUMENT_FOUND: (ResponseStatus.OUT_OF_KNOWLEDGE_BASE, SuggestionType.ASK_MORE_FACTS, RiskLevel.UNKNOWN),
            LegalQueryIntent.LOW_RETRIEVAL_CONFIDENCE: (ResponseStatus.LOW_CONFIDENCE, SuggestionType.ASK_MORE_FACTS, RiskLevel.UNKNOWN),
            LegalQueryIntent.PARTIAL_KB_COVERAGE: (ResponseStatus.OUT_OF_KNOWLEDGE_BASE, SuggestionType.ASK_MORE_FACTS, RiskLevel.MEDIUM),
            LegalQueryIntent.OUTDATED_KNOWLEDGE_BASE: (ResponseStatus.OUT_OF_KNOWLEDGE_BASE, SuggestionType.ASK_MORE_FACTS, RiskLevel.UNKNOWN),
            LegalQueryIntent.CONFLICTING_REFERENCES: (ResponseStatus.LOW_CONFIDENCE, SuggestionType.ASK_MORE_FACTS, RiskLevel.UNKNOWN),
        }
        response_status, suggestion_type, risk_level = mapping[issue_intent]
        from app.models.intent_enums import Jurisdiction, ResponseMode, UserRole
        from app.services.intent_detector import IntentResult

        return IntentResult(
            intent=issue_intent,
            contract_type=ContractType.UNKNOWN,
            user_role=UserRole.UNKNOWN,
            jurisdiction=Jurisdiction.VIETNAM,
            response_mode=ResponseMode.SAFE_REDIRECT,
            response_status=response_status,
            suggestion_type=suggestion_type,
            risk_level=risk_level,
            confidence=0.8,
            has_document_context=False,
        )

    def _build_guard_response(self, *, request, intent_result, completeness, user_hits, knowledge_hits):
        answer = self._guard_answer(intent_result.intent, completeness.questions_to_ask)
        is_signing_decision = intent_result.intent == LegalQueryIntent.SIGNING_DECISION_SUPPORT
        suggestion_type = (
            SuggestionType.SUGGEST_LAWYER.value
            if is_signing_decision
            else intent_result.suggestion_type.value
        )
        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=answer,
            confidenceScore=intent_result.confidence,
            shouldSuggestTicket=is_signing_decision,
            suggestionType=suggestion_type,
            suggestionReason=self._default_suggestion_reason(suggestion_type),
            missingInformation=self._missing_information_text(completeness.questions_to_ask),
            riskLevel=intent_result.risk_level.value,
            legalDomain=self._default_legal_domain(intent_result.contract_type),
            userActionHint=(
                "CREATE_TICKET"
                if is_signing_decision
                else self._map_user_action_hint(suggestion_type, intent_result.response_status.value)
            ),
            citations=[],
            usedKnowledgeCitationIds=[],
            usedUserCitationIds=[],
            retrievedUserChunks=len(user_hits),
            retrievedKnowledgeChunks=len(knowledge_hits),
            intent=intent_result.intent.value,
            contractType=intent_result.contract_type.value,
            userRole=intent_result.user_role.value,
            jurisdiction=intent_result.jurisdiction.value,
            responseStatus=intent_result.response_status.value,
            responseMode=intent_result.response_mode.value,
            inputComplete=completeness.is_complete,
            missingInputs=completeness.missing_items or None,
        )

    def _guard_answer(self, intent: LegalQueryIntent, questions: list[str]) -> str:
        mapping = {
            LegalQueryIntent.OUT_OF_DOMAIN_GENERAL_QUERY: "Mình chỉ hỗ trợ phân tích hợp đồng đơn giản trong phạm vi sinh viên. Bạn có thể hỏi về hợp đồng thuê trọ, làm thêm/thực tập, dịch vụ nhỏ, mua bán tài sản nhỏ hoặc giấy vay tiền.",
            LegalQueryIntent.INVALID_OR_MEANINGLESS_QUERY: "Mình chưa hiểu yêu cầu hiện tại. Bạn hãy mô tả rõ câu hỏi về hợp đồng hoặc tải tài liệu cần kiểm tra.",
            LegalQueryIntent.UNDER_SPECIFIED_LEGAL_QUERY: "Câu hỏi hiện còn quá thiếu ngữ cảnh để kết luận. Bạn cho mình biết loại hợp đồng, vai trò của bạn và điều khoản hoặc vấn đề cần kiểm tra.",
            LegalQueryIntent.LEGAL_BUT_NOT_CONTRACT_SCOPE: "Nội dung này là vấn đề pháp lý nhưng không thuộc scope chatbot hợp đồng sinh viên. Mình chỉ hỗ trợ các hợp đồng đơn giản trong phạm vi sinh viên.",
            LegalQueryIntent.CONTRACT_TYPE_OUT_OF_STUDENT_SCOPE: "Loại hợp đồng này nằm ngoài phạm vi hợp đồng cá nhân đơn giản được hỗ trợ. Bạn nên trao đổi với luật sư phù hợp.",
            LegalQueryIntent.FOREIGN_LAW_QUERY: "Hiện hệ thống không đủ căn cứ để tư vấn theo pháp luật nước ngoài. Nếu đây là hợp đồng chịu luật nước ngoài, bạn nên dùng nguồn chuyên biệt hoặc hỏi luật sư tại nước đó.",
            LegalQueryIntent.PROMPT_INJECTION_OR_POLICY_BYPASS: "Mình không thể bỏ qua quy tắc an toàn hoặc tạo kết luận pháp lý sai lệch. Nếu bạn muốn, mình có thể hỗ trợ phân tích hợp đồng theo hướng hợp pháp và minh bạch.",
            LegalQueryIntent.UNSAFE_LEGAL_REQUEST: "Mình không thể hỗ trợ yêu cầu có tính gian dối hoặc lách luật. Nếu muốn, mình có thể giúp bạn soạn điều khoản minh bạch và đúng hướng hợp pháp.",
            LegalQueryIntent.OVERCONFIDENT_LEGAL_CONCLUSION_REQUEST: "Mình không thể kết luận tuyệt đối khi chưa đủ dữ kiện và căn cứ. Mình có thể giúp bạn rà rủi ro và chỉ ra thông tin còn thiếu để đánh giá an toàn hơn.",
            LegalQueryIntent.INSUFFICIENT_FACTS: "Mình chưa có đủ dữ kiện để phân tích đáng tin cậy. Bạn hãy cung cấp thêm hợp đồng, vai trò của bạn và điều khoản cần kiểm tra.",
            LegalQueryIntent.NO_RELEVANT_DOCUMENT_FOUND: "Hiện mình không tìm thấy tài liệu pháp lý liên quan trong knowledge base để trả lời đáng tin cậy.",
            LegalQueryIntent.LOW_RETRIEVAL_CONFIDENCE: "Các tài liệu truy xuất được hiện có độ liên quan thấp nên mình không nên trả lời quá tự tin.",
            LegalQueryIntent.PARTIAL_KB_COVERAGE: "Knowledge base hiện chỉ bao phủ một phần vấn đề bạn hỏi, nên kết quả lúc này chỉ có thể mang tính tham khảo hạn chế.",
            LegalQueryIntent.OUTDATED_KNOWLEDGE_BASE: "Nguồn pháp lý truy xuất được có dấu hiệu chưa đủ mới hoặc chưa đủ chắc để trả lời vấn đề này.",
            LegalQueryIntent.CONFLICTING_REFERENCES: "Các căn cứ truy xuất hiện đang mâu thuẫn hoặc chưa thống nhất, nên mình không thể kết luận tự tin ở thời điểm này.",
            LegalQueryIntent.SIGNING_DECISION_SUPPORT: (
                "Hệ thống không khuyến khích bạn ký hợp đồng chỉ dựa trên phân tích của AI. "
                "Thông tin được cung cấp chỉ nhằm hỗ trợ bạn tự ra quyết định và không thay thế việc xem xét của chuyên gia. "
                "Nếu muốn được hỗ trợ thêm trước khi ký, bạn có thể tạo ticket để chuyên gia xem xét."
            ),
        }
        answer = mapping.get(intent, "Mình cần thêm thông tin để tiếp tục phân tích.")
        if questions:
            answer = f"{answer}\n\nThông tin mình cần thêm:\n- " + "\n- ".join(questions[:3])
        return answer

    def _default_suggestion_reason(self, suggestion_type: str) -> str | None:
        reasons = {
            "ASK_UPLOAD_CONTRACT": "Cần hợp đồng cụ thể để phân tích chính xác.",
            "ASK_CONTRACT_TYPE": "Cần xác định đúng loại hợp đồng trong scope hỗ trợ.",
            "ASK_USER_ROLE": "Vai trò của bạn ảnh hưởng trực tiếp đến đánh giá rủi ro và khuyến nghị.",
            "ASK_TARGET_CLAUSE": "Cần biết điều khoản mục tiêu để phân tích đúng trọng tâm.",
            "ASK_MORE_FACTS": "Hiện còn thiếu dữ kiện để đưa ra nhận định đáng tin cậy.",
            "SUGGEST_REVISE_CLAUSE": "Có điểm cần sửa hoặc làm rõ trong điều khoản.",
            "SUGGEST_NEGOTIATION": "Có nội dung nên đàm phán lại trước khi ký.",
            "SUGGEST_LAWYER": "Mức rủi ro hoặc độ không chắc chắn đủ cao để nên tham khảo luật sư.",
            "REDIRECT_TO_SUPPORTED_SCOPE": "Nội dung hiện không thuộc phạm vi hỗ trợ của chatbot.",
            "REFUSE_AND_REDIRECT": "Yêu cầu hiện không an toàn hoặc không phù hợp để hỗ trợ.",
            "DIRECT_ANSWER": "Có thể trả lời trong phạm vi hiện có.",
        }
        return reasons.get(suggestion_type)

    def _missing_information_text(self, questions: list[str]) -> str | None:
        if not questions:
            return None
        return " | ".join(questions[:3])

    def _default_legal_domain(self, contract_type: ContractType) -> str | None:
        mapping = {
            ContractType.RENTAL: "rental",
            ContractType.PART_TIME_EMPLOYMENT: "employment",
            ContractType.INTERNSHIP: "employment",
            ContractType.COLLABORATOR: "employment",
            ContractType.FREELANCE_SERVICE: "service",
            ContractType.SMALL_ASSET_SALE: "sale",
            ContractType.PERSONAL_LOAN: "loan",
        }
        return mapping.get(contract_type)

    def _map_user_action_hint(self, suggestion_type: str, response_status: str) -> str:
        if suggestion_type == SuggestionType.ASK_UPLOAD_CONTRACT.value:
            return "UPLOAD_CONTRACT"
        if suggestion_type in {
            SuggestionType.ASK_CONTRACT_TYPE.value,
            SuggestionType.ASK_USER_ROLE.value,
            SuggestionType.ASK_TARGET_CLAUSE.value,
            SuggestionType.ASK_MORE_FACTS.value,
        } or response_status in {
            ResponseStatus.NEED_MORE_INFORMATION.value,
            ResponseStatus.INCOMPLETE_INPUT.value,
        }:
            return "PROVIDE_MORE_INFO"
        if suggestion_type == SuggestionType.SUGGEST_LAWYER.value or response_status == ResponseStatus.HIGH_RISK_REQUIRE_LAWYER.value:
            return "CONTACT_LAWYER"
        return "CONTINUE_CHAT"

    def _postprocess_response_status(self, *, base_status: ResponseStatus, confidence_score: float | None, citations: list[RagCitation], risk_level: str) -> str:
        if risk_level in {"HIGH", "CRITICAL"}:
            return ResponseStatus.HIGH_RISK_REQUIRE_LAWYER.value
        if confidence_score is not None and confidence_score < 0.45:
            return ResponseStatus.LOW_CONFIDENCE.value
        if not citations:
            return ResponseStatus.OUT_OF_KNOWLEDGE_BASE.value
        return base_status.value

    def _validate_used_citations(
        self,
        *,
        answer: str,
        declared_knowledge_ids: tuple[str, ...],
        declared_user_ids: tuple[str, ...],
        user_hits: list[RagChunkHit],
        knowledge_hits: list[RagChunkHit],
    ) -> tuple[list[str], list[str], list[str]]:
        inline_ids = {match.upper() for match in _CITATION_PATTERN.findall(answer)}
        inline_knowledge_ids = {citation_id for citation_id in inline_ids if citation_id.startswith("KB-")}
        inline_user_ids = {citation_id for citation_id in inline_ids if citation_id.startswith("USER-")}
        declared_knowledge_set = {citation_id.upper() for citation_id in declared_knowledge_ids}
        declared_user_set = {citation_id.upper() for citation_id in declared_user_ids}
        available_knowledge_ids = {hit.citationId for hit in knowledge_hits}
        available_user_ids = {hit.citationId for hit in user_hits}
        invalid_ids = sorted(
            ((inline_knowledge_ids | declared_knowledge_set) - available_knowledge_ids)
            | ((inline_user_ids | declared_user_set) - available_user_ids)
        )
        used_knowledge_ids = [
            hit.citationId for hit in knowledge_hits if hit.citationId in inline_knowledge_ids
        ]
        used_user_ids = [hit.citationId for hit in user_hits if hit.citationId in inline_user_ids]
        return used_knowledge_ids, used_user_ids, invalid_ids

    def _should_suggest_ticket(
        self,
        *,
        intent: LegalQueryIntent,
        input_complete: bool,
        used_knowledge_ids: list[str],
        invalid_citation_ids: list[str],
        risk_level: str,
        response_status: str,
    ) -> bool:
        if intent == LegalQueryIntent.SIGNING_DECISION_SUPPORT:
            return True

        return (
            intent in _TICKET_ELIGIBLE_INTENTS
            and input_complete
            and bool(used_knowledge_ids)
            and not invalid_citation_ids
            and risk_level in {"HIGH", "CRITICAL"}
            and response_status == ResponseStatus.HIGH_RISK_REQUIRE_LAWYER.value
        )

    def _apply_signing_decision_safety(self, answer: str) -> str:
        notice = (
            "**Lưu ý trước khi ký:** Hệ thống không khuyến khích bạn ký hợp đồng chỉ dựa trên "
            "phân tích của AI. Thông tin dưới đây chỉ nhằm hỗ trợ bạn tự ra quyết định và không "
            "thay thế việc xem xét của chuyên gia."
        )
        ticket_option = (
            "Nếu muốn được hỗ trợ thêm trước khi ký, bạn có thể tạo ticket để chuyên gia xem xét."
        )
        return f"{notice}\n\n{answer.strip()}\n\n{ticket_option}"

    def _recommendation_section_is_grounded(self, answer: str) -> bool:
        recommendation_match = re.search(
            r"(?:^\s*###+\s*(?:khuyến nghị|recommendations?)|^\s*(?:\*\*|__)(?:khuyến nghị|recommendations?)(?:\*\*|__):?|^\s*(?:khuyến nghị|recommendations?)\s*:)\s*(.*)$",
            answer,
            flags=re.IGNORECASE | re.MULTILINE | re.DOTALL,
        )
        if recommendation_match is None:
            return True
        section = recommendation_match.group(1).strip()
        if not section:
            return True
        bullet_blocks = re.split(r"(?m)(?=^\s*(?:[-*•]|\d+[.)])\s+)", section)
        actual_bullets = [
            block for block in bullet_blocks if re.match(r"^\s*(?:[-*•]|\d+[.)])\s+", block)
        ]
        if actual_bullets:
            return all(re.search(r"\[KB-\d+\]", block, flags=re.IGNORECASE) for block in actual_bullets)
        return True

    def _filter_ungrounded_recommendation_items(self, answer: str) -> tuple[str, int]:
        heading = re.search(
            r"(?:^\s*###+\s*(?:khuyến nghị|recommendations?)|^\s*(?:\*\*|__)(?:khuyến nghị|recommendations?)(?:\*\*|__):?|^\s*(?:khuyến nghị|recommendations?)\s*:)",
            answer,
            flags=re.IGNORECASE | re.MULTILINE,
        )
        if heading is None:
            return answer, 0

        prefix = answer[: heading.start()]
        section = answer[heading.end() :]
        blocks = re.split(r"(?m)(?=^\s*(?:[-*•]|\d+[.)])\s+)", section)
        kept: list[str] = []
        removed = 0
        for block in blocks:
            is_bullet = bool(re.match(r"^\s*(?:[-*•]|\d+[.)])\s+", block))
            if is_bullet and not re.search(r"\[KB-\d+\]", block, flags=re.IGNORECASE):
                removed += 1
                continue
            kept.append(block)

        remaining_section = "".join(kept).strip()
        if not remaining_section:
            return prefix.rstrip(), removed

        return (answer[: heading.end()] + "".join(kept)).rstrip(), removed

    def _grounding_failure_answer(self, has_invalid_citation: bool) -> str:
        if has_invalid_citation:
            return (
                "Câu trả lời vừa tạo có căn cứ không khớp với tài liệu đã truy xuất, "
                "nên mình chưa thể cung cấp kết luận pháp lý đáng tin cậy."
            )
        return (
            "Knowledge base hiện chưa cung cấp đủ căn cứ pháp lý được trích dẫn "
            "để mình trả lời đáng tin cậy."
        )

    def _fetch_available_docs(self, request: RagQueryRequest) -> tuple[list[str], list[str]]:
        available_user_docs: list[str] = []
        available_system_docs: list[str] = []
        try:
            from app.database.neo4j_client import neo4j_client
            import json
            if not neo4j_client.driver:
                neo4j_client.connect()
            docs = neo4j_client.execute_query("MATCH (d:Document) RETURN d.title as title, d.metadata_json as metadata_json")
            for doc in docs:
                title = doc.get("title") or "Untitled"
                metadata = {}
                if doc.get("metadata_json"):
                    try:
                        metadata = json.loads(doc["metadata_json"])
                    except Exception:
                        metadata = {}
                ws_id = metadata.get("workspace_id") or metadata.get("workspaceId")
                if is_published_system_kb(metadata):
                    available_system_docs.append(title)
                elif ws_id == request.workspaceId:
                    available_user_docs.append(title)
        except Exception as exc:
            logger.error("Failed to fetch documents list: %s", exc)
        return sorted(set(available_user_docs)), sorted(set(available_system_docs))

    def _build_fallback_answer(self, user_hits: list[RagChunkHit], knowledge_hits: list[RagChunkHit]) -> str:
        if not user_hits and not knowledge_hits:
            return "Hiện mình chưa tìm thấy thông tin liên quan trong tài liệu và knowledge base."
        return "Hiện chưa thể sinh phân tích hoàn chỉnh từ mô hình AI. Bạn có thể thử hỏi ngắn gọn hơn hoặc kiểm tra lại dữ liệu đầu vào."

    def _to_citation(self, hit: RagChunkHit) -> RagCitation:
        return RagCitation(
            citationId=hit.citationId,
            sourceType=hit.sourceType,
            score=hit.score,
            documentId=hit.documentId,
            workspaceId=hit.workspaceId,
            userId=hit.userId,
            fileName=hit.fileName,
            knowledgeDocumentId=hit.knowledgeDocumentId,
            lawName=hit.lawName,
            lawCode=hit.lawCode,
            legalDomain=hit.legalDomain,
            pageNumber=hit.pageNumber,
            articleNumber=hit.articleNumber,
            clauseNumber=hit.clauseNumber,
            sectionTitle=hit.sectionTitle,
            excerpt=hit.chunkText,
        )

    def _to_preview_chunk(self, hit: RagChunkHit) -> RagPreviewChunk:
        return RagPreviewChunk(
            citationId=hit.citationId,
            sourceType=hit.sourceType,
            score=hit.score,
            chunkText=hit.chunkText,
            documentId=hit.documentId,
            workspaceId=hit.workspaceId,
            userId=hit.userId,
            fileName=hit.fileName,
            knowledgeDocumentId=hit.knowledgeDocumentId,
            lawName=hit.lawName,
            lawCode=hit.lawCode,
            legalDomain=hit.legalDomain,
            pageNumber=hit.pageNumber,
            articleNumber=hit.articleNumber,
            clauseNumber=hit.clauseNumber,
            sectionTitle=hit.sectionTitle,
        )

    def _enrich_answer_with_download_links(
        self, answer: str, workspace_id: str, knowledge_hits: list[RagChunkHit]
    ) -> str:
        if not answer or not knowledge_hits or not workspace_id:
            return answer

        kb_map = {}
        for hit in knowledge_hits:
            if hit.citationId:
                file_target = (
                    (hit.fileName or "").strip()
                    or (hit.lawName or "").strip()
                    or (hit.title or "").strip()
                    or (hit.knowledgeDocumentId or "").strip()
                )
                if file_target:
                    kb_map[hit.citationId] = file_target

        logger.info("DEBUG ENRICH DOWNLOAD LINKS: kb_map=%s, answer_has_kb=%s", kb_map, "[KB-" in answer)
        if not kb_map:
            return answer

        import re
        from urllib.parse import quote

        def _replace_tag(match):
            tag = match.group(1)
            if tag in kb_map:
                filename = kb_map[tag]
                encoded = quote(filename)
                download_url = f"/api/v1/workspaces/{workspace_id}/documents/system/download?filename={encoded}"
                return f"[[{tag} 📥 Tải về]]({download_url})"
            return match.group(0)

        return re.sub(r"\[(KB-\d+)\]", _replace_tag, answer)
