from __future__ import annotations

import logging

from app.models.intent_enums import LegalQueryIntent, ResponseMode
from app.schemas import RagCitation, RagPreviewChunk, RagPreviewResponse, RagQueryRequest, RagQueryResponse
from app.services.completeness_checker import check_completeness
from app.services.llm_intent_detector import detect_intent_smart
from app.services.llm_client import RagLlmClient, build_default_llm_client
from app.services.prompt_builder import build_intent_instruction, build_system_prompt, build_user_prompt
from app.services.query_builder import build_legal_search_query
from app.services.retrieval_service import RagChunkHit, RetrievalService


logger = logging.getLogger(__name__)


class RagQueryService:
    def __init__(
        self,
        *,
        retrieval_service: RetrievalService | None = None,
        llm_client: RagLlmClient | None = None,
    ) -> None:
        self.retrieval_service = retrieval_service or RetrievalService()
        self.llm_client = llm_client or build_default_llm_client()

    def query(self, request: RagQueryRequest) -> RagQueryResponse:
        # ── Step 0a: Export chat to DOCX shortcut ──
        from app.services.contract_generation_service import (
            ContractGenerationService,
            is_contract_generation_intent,
            is_export_docx_intent,
        )
        if is_export_docx_intent(request.question):
            gen_service = ContractGenerationService(
                retrieval_service=self.retrieval_service,
                llm_client=self.llm_client,
            )
            return gen_service.export_chat_to_docx(request)

        # ── Step 0b: Contract generation shortcut (existing) ──
        if is_contract_generation_intent(request.question):
            gen_service = ContractGenerationService(
                retrieval_service=self.retrieval_service,
                llm_client=self.llm_client
            )
            return gen_service.generate_contract(request)

        # ── Step 1: Retrieve chunks ──
        user_hits, legal_search_query, knowledge_hits = self._retrieve(request)

        # ── Step 2: Detect intent ──
        intent_result = detect_intent_smart(
            request.question,
            has_user_chunks=len(user_hits) > 0,
            has_knowledge_chunks=len(knowledge_hits) > 0,
        )
        logger.info(
            "Intent detected: %s (contract_type=%s, mode=%s, confidence=%.2f)",
            intent_result.intent.value,
            intent_result.contract_type.value,
            intent_result.response_mode.value,
            intent_result.confidence,
        )

        # ── Step 3: Check input completeness ──
        completeness = check_completeness(
            intent_result.intent,
            has_user_chunks=len(user_hits) > 0,
            has_knowledge_chunks=len(knowledge_hits) > 0,
            question=request.question,
        )

        # ── Step 4: Handle UNSAFE requests ──
        if intent_result.intent == LegalQueryIntent.UNSAFE_LEGAL_REQUEST:
            return self._build_unsafe_response(request, intent_result, user_hits, knowledge_hits)

        # ── Step 5: Retrieve available documents list ──
        available_user_docs, available_system_docs = self._fetch_available_docs(request)

        # ── Step 6: Build prompts (with intent context) ──
        system_prompt = build_system_prompt()

        user_prompt = build_user_prompt(
            request.question,
            user_hits,
            knowledge_hits,
            chat_history=request.chatHistory,
            available_user_docs=available_user_docs,
            available_system_docs=available_system_docs,
            workspace_id=request.workspaceId,
        )

        # Append intent-specific instructions
        intent_instruction = build_intent_instruction(
            intent_result.intent,
            contract_type=intent_result.contract_type,
            response_mode=intent_result.response_mode,
            completeness_questions=completeness.questions_to_ask if not completeness.is_complete else None,
        )
        user_prompt = user_prompt + "\n\n" + intent_instruction

        # ── Step 7: Call LLM ──
        llm_result = self.llm_client.generate(system_prompt=system_prompt, user_prompt=user_prompt)

        raw_answer = llm_result.answer or self._build_fallback_answer(user_hits, knowledge_hits)
        from app.services.llm_client import sanitize_response
        answer = sanitize_response(raw_answer)

        # ── Step 8: Logging ──
        logger.info("=== RAG QUERY SERVICE LOGGING ===")
        logger.info(f"Intent: {intent_result.intent.value}")
        logger.info(f"Contract Type: {intent_result.contract_type.value}")
        logger.info(f"Response Mode: {intent_result.response_mode.value}")
        logger.info(f"Input Complete: {completeness.is_complete}")
        logger.info(f"Available User Docs: {available_user_docs}")
        logger.info(f"Available System Docs: {available_system_docs}")
        logger.info(f"User Question: {request.question}")
        logger.info(f"Retrieved User Chunks (Total: {len(user_hits)}):")
        for idx, hit in enumerate(user_hits):
            logger.info(f"  User Chunk {idx + 1}: Doc={hit.fileName}, TextSnippet='{hit.chunkText[:150]}...'")
        logger.info(f"Retrieved Knowledge Chunks (Total: {len(knowledge_hits)}):")
        for idx, hit in enumerate(knowledge_hits):
            logger.info(f"  Knowledge Chunk {idx + 1}: LawCode={hit.lawCode}, TextSnippet='{hit.chunkText[:150]}...'")
        logger.info(f"Final Prompt (System):\n{system_prompt}")
        logger.info(f"Final Prompt (User):\n{user_prompt}")
        logger.info(f"Raw Gemini Response:\n{llm_result.raw_response}")
        logger.info(f"Sanitized Response:\n{answer}")
        logger.info("=================================")

        # ── Step 9: Determine risk/suggestion/action based on intent ──
        risk_level = llm_result.risk_level if llm_result.risk_level else "UNKNOWN"
        if risk_level not in {"LOW", "MEDIUM", "HIGH", "NEED_EXPERT", "UNKNOWN"}:
            risk_level = "UNKNOWN"

        suggestion_type = llm_result.suggestion_type
        user_action_hint = llm_result.user_action_hint
        should_suggest_ticket = llm_result.should_suggest_ticket

        # Override based on intent if LLM didn't set meaningful values
        suggestion_type, user_action_hint, should_suggest_ticket = self._enrich_from_intent(
            intent_result,
            completeness,
            suggestion_type=suggestion_type,
            user_action_hint=user_action_hint,
            should_suggest_ticket=should_suggest_ticket,
            risk_level=risk_level,
        )

        # ── Step 10: Build enriched response ──
        citations = [self._to_citation(hit) for hit in [*user_hits, *knowledge_hits]]
        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=answer,
            confidenceScore=llm_result.confidence_score,
            shouldSuggestTicket=should_suggest_ticket,
            suggestionType=suggestion_type,
            suggestionReason=llm_result.suggestion_reason,
            missingInformation=llm_result.missing_information,
            riskLevel=risk_level,
            legalDomain=llm_result.legal_domain,
            userActionHint=user_action_hint,
            citations=citations,
            retrievedUserChunks=len(user_hits),
            retrievedKnowledgeChunks=len(knowledge_hits),
            # NEW fields
            intent=intent_result.intent.value,
            contractType=intent_result.contract_type.value,
            responseMode=intent_result.response_mode.value,
            inputComplete=completeness.is_complete,
            missingInputs=completeness.missing_items if completeness.missing_items else None,
            analysis=llm_result.analysis,
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
        user_hits = self.retrieval_service.search_user_chunks(
            request.question,
            user_id=request.userId,
            workspace_id=request.workspaceId,
            top_k=request.topKUserChunks,
        )
        legal_search_query = build_legal_search_query(request.question, user_hits)
        knowledge_hits = self.retrieval_service.search_knowledge_chunks(
            legal_search_query,
            top_k=request.topKKnowledgeChunks,
            query_text=request.question,
        )
        return user_hits, legal_search_query, knowledge_hits

    def _build_fallback_answer(self, user_hits: list[RagChunkHit], knowledge_hits: list[RagChunkHit]) -> str:
        if not user_hits and not knowledge_hits:
            return "Không tìm thấy thông tin liên quan trong tài liệu và cơ sở tri thức."
        return "Hệ thống không thể kết nối tới mô hình AI để tự động phân tích điều khoản này. Vui lòng cấu hình API Key hợp lệ."

    def _build_unsafe_response(self, request, intent_result, user_hits, knowledge_hits):
        """Handle Case 11: Unsafe/unethical legal requests."""
        from app.services.completeness_checker import CompletenessResult
        answer = (
            "## ⛔ Yêu cầu không được hỗ trợ\n\n"
            "Tôi không thể hỗ trợ yêu cầu này vì nó có dấu hiệu **không phù hợp với đạo đức nghề nghiệp** "
            "và **pháp luật hiện hành**.\n\n"
            "### Lý do\n"
            "- Việc soạn thảo điều khoản nhằm che giấu thông tin hoặc gây bất lợi cho một bên "
            "vi phạm nguyên tắc **trung thực và thiện chí** (Điều 3 Bộ luật Dân sự 2015).\n"
            "- Hợp đồng có điều khoản lừa dối có thể bị **tuyên vô hiệu** theo Điều 127 BLDS 2015.\n\n"
            "### Thay vào đó, tôi có thể giúp bạn\n"
            "- ✅ Soạn điều khoản **minh bạch và cân bằng** quyền lợi các bên\n"
            "- ✅ Phân tích **rủi ro pháp lý** của hợp đồng hiện có\n"
            "- ✅ Đề xuất **biện pháp bảo vệ quyền lợi hợp pháp** cho bạn\n"
            "- ✅ Kiểm tra hợp đồng có **phù hợp pháp luật** không\n\n"
            "Bạn muốn tôi giúp gì trong các lựa chọn trên?"
        )
        citations = [self._to_citation(hit) for hit in [*user_hits, *knowledge_hits]]
        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=answer,
            confidenceScore=0.95,
            shouldSuggestTicket=False,
            suggestionType="NONE",
            suggestionReason="Yêu cầu không an toàn đã bị từ chối.",
            missingInformation=None,
            riskLevel="HIGH",
            legalDomain=None,
            userActionHint="CONTINUE_CHAT",
            citations=citations,
            retrievedUserChunks=len(user_hits),
            retrievedKnowledgeChunks=len(knowledge_hits),
            intent=intent_result.intent.value,
            contractType=intent_result.contract_type.value,
            responseMode=intent_result.response_mode.value,
            inputComplete=True,
            missingInputs=None,
        )

    def _fetch_available_docs(self, request: RagQueryRequest) -> tuple[list[str], list[str]]:
        """Fetch available document titles from Neo4j for context."""
        available_user_docs: list[str] = []
        available_system_docs: list[str] = []
        try:
            from app.database.neo4j_client import neo4j_client
            import json
            if not neo4j_client.driver:
                neo4j_client.connect()
            docs = neo4j_client.execute_query(
                "MATCH (d:Document) RETURN d.title as title, d.metadata_json as metadata_json"
            )
            user_docs: list[str] = []
            system_docs: list[str] = []
            for d in docs:
                title = d.get("title") or "Untitled"
                metadata: dict = {}
                if d.get("metadata_json"):
                    try:
                        metadata = json.loads(d["metadata_json"])
                    except Exception:
                        pass

                ws_id = metadata.get("workspace_id") or metadata.get("workspaceId")
                u_id = metadata.get("user_id") or metadata.get("userId")

                if ws_id == request.workspaceId:
                    user_docs.append(title)
                elif metadata.get("source_type") == "SYSTEM_KB" or not u_id:
                    system_docs.append(title)

            available_user_docs = sorted(set(user_docs))
            available_system_docs = sorted(set(system_docs))
        except Exception as e:
            logger.error("Failed to fetch documents list: %s", e)
        return available_user_docs, available_system_docs

    def _enrich_from_intent(
        self,
        intent_result,
        completeness,
        *,
        suggestion_type: str,
        user_action_hint: str,
        should_suggest_ticket: bool,
        risk_level: str,
    ) -> tuple[str, str, bool]:
        """Override suggestion/action fields based on detected intent when LLM defaults are generic."""
        intent = intent_result.intent

        # ── If input is incomplete, ask for more info ──
        if not completeness.is_complete:
            if "document" in completeness.missing_items:
                return "ASK_MORE_INFO", "UPLOAD_CONTRACT", False
            return "ASK_MORE_INFO", "PROVIDE_MORE_INFO", False

        # ── Intent-specific overrides ──
        if intent == LegalQueryIntent.LEGAL_VALIDITY_CHECK:
            if risk_level in ("HIGH", "NEED_EXPERT"):
                return "SUGGEST_LAWYER", "CONTACT_LAWYER", True
            if suggestion_type == "NONE":
                return "SUGGEST_LAWYER", "CONTINUE_CHAT", True

        if intent == LegalQueryIntent.SIGNING_DECISION_SUPPORT:
            if risk_level in ("HIGH", "NEED_EXPERT"):
                return "REQUIRE_LAWYER", "CONTACT_LAWYER", True
            if suggestion_type == "NONE":
                return "SUGGEST_LAWYER", "CONTINUE_CHAT", True

        if intent == LegalQueryIntent.NEED_MORE_INFO:
            return "ASK_MORE_INFO", "PROVIDE_MORE_INFO", False

        if intent == LegalQueryIntent.OUT_OF_KNOWLEDGE_BASE:
            return "ASK_MORE_INFO", "CONTINUE_CHAT", False

        if intent == LegalQueryIntent.FULL_CONTRACT_REVIEW:
            if risk_level in ("HIGH", "NEED_EXPERT") and suggestion_type == "NONE":
                return "SUGGEST_LAWYER", "CREATE_TICKET", True

        if intent == LegalQueryIntent.MISSING_CLAUSE_CHECK:
            if risk_level in ("HIGH", "NEED_EXPERT") and suggestion_type == "NONE":
                return "SUGGEST_LAWYER", "CONTINUE_CHAT", True

        # Default: keep LLM values
        return suggestion_type, user_action_hint, should_suggest_ticket


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
