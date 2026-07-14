from __future__ import annotations

import logging
import re

from app.models.intent_enums import (
    ContractType,
    LegalQueryIntent,
    ResponseStatus,
    RiskLevel,
    SuggestionType,
)
from app.schemas import RagCitation, RagPreviewChunk, RagPreviewResponse, RagQueryRequest, RagQueryResponse, RagUsage
from app.services.completeness_checker import check_completeness
from app.services.intent_detector import detect_intent
from app.services.llm_client import RagLlmClient, build_default_llm_client, sanitize_response
from app.services.prompt_builder import build_intent_instruction, build_system_prompt, build_user_prompt
from app.services.query_builder import build_legal_search_query, build_legal_text_query, extract_recent_user_history
from app.services.retrieval_service import RagChunkHit, RetrievalService


logger = logging.getLogger(__name__)

_CITATION_PATTERN = re.compile(r"\[((?:KB|USER)-\d+)\]", re.IGNORECASE)
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
    ) -> None:
        self.retrieval_service = retrieval_service or RetrievalService()
        self.llm_client = llm_client or build_default_llm_client()

    def query(self, request: RagQueryRequest) -> RagQueryResponse:
        from app.services.contract_generation_service import ContractGenerationService, is_contract_generation_intent

        if is_contract_generation_intent(request.question):
            gen_service = ContractGenerationService(
                retrieval_service=self.retrieval_service,
                llm_client=self.llm_client,
            )
            return gen_service.generate_contract(request)

        user_hits, legal_search_query, knowledge_hits = self._retrieve(request)
        intent_result = detect_intent(
            request.question,
            has_user_chunks=bool(user_hits),
            has_knowledge_chunks=bool(knowledge_hits),
            conversation_context=extract_recent_user_history(request.chatHistory),
        )
        completeness = check_completeness(
            intent_result.intent,
            contract_type=intent_result.contract_type,
            user_role=intent_result.user_role,
            has_user_chunks=bool(user_hits),
            question=request.question,
            conversation_context=extract_recent_user_history(request.chatHistory),
        )

        if self._should_short_circuit(intent_result.intent):
            return self._build_guard_response(
                request=request,
                intent_result=intent_result,
                completeness=completeness,
                user_hits=user_hits,
                knowledge_hits=knowledge_hits,
            )

        if not completeness.is_complete:
            adjusted_intent_result = self._adjust_for_completeness(intent_result, completeness)
            return self._build_guard_response(
                request=request,
                intent_result=adjusted_intent_result,
                completeness=completeness,
                user_hits=user_hits,
                knowledge_hits=knowledge_hits,
            )

        retrieval_issue = self._detect_retrieval_issue(request.question, intent_result.intent, knowledge_hits)
        if retrieval_issue is not None:
            return self._build_guard_response(
                request=request,
                intent_result=retrieval_issue,
                completeness=completeness,
                user_hits=user_hits,
                knowledge_hits=knowledge_hits,
            )

        available_user_docs, available_system_docs = self._fetch_available_docs(request)
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
        user_prompt += build_intent_instruction(
            intent_result.intent,
            contract_type=intent_result.contract_type,
            response_mode=intent_result.response_mode,
            completeness_questions=None,
        )

        llm_result = self.llm_client.generate(system_prompt=system_prompt, user_prompt=user_prompt)
        if llm_result.error or not llm_result.answer:
            raise LlmGenerationError(llm_result.error or "LLM returned an empty answer")
        answer = sanitize_response(llm_result.answer or self._build_fallback_answer(user_hits, knowledge_hits))
        answer, removed_recommendations = self._filter_ungrounded_recommendation_items(answer)
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
            user_hits=user_hits,
            knowledge_hits=knowledge_hits,
        )
        used_ids = {*used_knowledge_ids, *used_user_ids}
        citations = [self._to_citation(hit) for hit in [*user_hits, *knowledge_hits] if hit.citationId in used_ids]
        recommendations_grounded = self._recommendation_section_is_grounded(answer)
        grounding_failed = bool(invalid_citation_ids) or (
            intent_result.intent in _LEGAL_GROUNDED_INTENTS and not used_knowledge_ids
        ) or (intent_result.intent in _LEGAL_GROUNDED_INTENTS and not recommendations_grounded)
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

        effective_confidence = llm_result.confidence_score
        retrieved_citations = [self._to_citation(hit) for hit in [*user_hits, *knowledge_hits]]
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
            user_action_hint = "CREATE_TICKET"

        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=answer,
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
            document_id=request.documentId,
        )
        legal_search_query = build_legal_search_query(
            request.question,
            user_hits,
            chat_history=request.chatHistory,
        )
        knowledge_hits = self.retrieval_service.search_knowledge_chunks(
            legal_search_query,
            top_k=request.topKKnowledgeChunks,
            query_text=build_legal_text_query(request.question, chat_history=request.chatHistory),
        )
        return user_hits, legal_search_query, knowledge_hits

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
        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=answer,
            confidenceScore=intent_result.confidence,
            shouldSuggestTicket=False,
            suggestionType=intent_result.suggestion_type.value,
            suggestionReason=self._default_suggestion_reason(intent_result.suggestion_type.value),
            missingInformation=self._missing_information_text(completeness.questions_to_ask),
            riskLevel=intent_result.risk_level.value,
            legalDomain=self._default_legal_domain(intent_result.contract_type),
            userActionHint=self._map_user_action_hint(intent_result.suggestion_type.value, intent_result.response_status.value),
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
            LegalQueryIntent.CONTRACT_TYPE_OUT_OF_STUDENT_SCOPE: "Loại hợp đồng này vượt ngoài phạm vi hợp đồng đơn giản dành cho sinh viên. Bạn nên trao đổi với luật sư để được đánh giá chính xác hơn.",
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
            ContractType.STUDENT_RENTAL: "student_rental",
            ContractType.PART_TIME_OR_INTERNSHIP: "employment",
            ContractType.SMALL_SERVICE_OR_FREELANCE: "service",
            ContractType.SMALL_ASSET_SALE: "sale",
            ContractType.PERSONAL_LOAN_SIMPLE: "loan",
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
        return (
            intent in _TICKET_ELIGIBLE_INTENTS
            and input_complete
            and bool(used_knowledge_ids)
            and not invalid_citation_ids
            and risk_level in {"HIGH", "CRITICAL"}
            and response_status == ResponseStatus.HIGH_RISK_REQUIRE_LAWYER.value
        )

    def _recommendation_section_is_grounded(self, answer: str) -> bool:
        recommendation_match = re.search(
            r"(?:khuyến nghị|recommendations?)\s*:?\**\s*(.*)$",
            answer,
            flags=re.IGNORECASE | re.DOTALL,
        )
        if recommendation_match is None:
            return True
        section = recommendation_match.group(1)
        bullet_blocks = re.split(r"(?m)(?=^\s*(?:[-*•]|\d+[.)])\s+)", section)
        actual_bullets = [
            block for block in bullet_blocks if re.match(r"^\s*(?:[-*•]|\d+[.)])\s+", block)
        ]
        if actual_bullets:
            return all(re.search(r"\[KB-\d+\]", block, flags=re.IGNORECASE) for block in actual_bullets)
        return bool(re.search(r"\[KB-\d+\]", section, flags=re.IGNORECASE))

    def _filter_ungrounded_recommendation_items(self, answer: str) -> tuple[str, int]:
        heading = re.search(
            r"(?:khuyến nghị|recommendations?)\s*:?\**",
            answer,
            flags=re.IGNORECASE,
        )
        if heading is None:
            return answer, 0

        prefix = answer[: heading.end()]
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
        return (prefix + "".join(kept)).rstrip(), removed

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
                if (
                    metadata.get("source_type") == "SYSTEM_KB"
                    and metadata.get("effective_status") == "ACTIVE"
                    and metadata.get("ingested_by_role") == "ADMIN"
                ):
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
