from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass

from app.graph.connection import get_driver
from app.schemas import ConversationMemoryUpdate, ConversationMessage, RagQueryRequest
from app.services.embedding_service import EmbeddingService
from app.services.llm_client import RagLlmClient


logger = logging.getLogger(__name__)


def estimate_tokens(text: str | None) -> int:
    if not text:
        return 0
    return max(1, (len(text) + 3) // 4)


def truncate_tokens(text: str, max_tokens: int) -> str:
    max_chars = max(0, max_tokens * 4)
    return text if len(text) <= max_chars else text[:max_chars]


@dataclass(frozen=True)
class PreparedConversationMemory:
    summary_json: str
    recent_history: str
    relevant_history: str
    memory_update: ConversationMemoryUpdate | None


class ConversationMemoryService:
    SUMMARY_TOKEN_LIMIT = 1_000
    RECENT_TOKEN_LIMIT = 3_000
    RELEVANT_TOKEN_LIMIT = 1_200
    RELEVANT_TURN_LIMIT = 5
    VECTOR_INDEX = "conversation_turn_embedding_idx"

    def __init__(self, embedding_service: EmbeddingService | None = None) -> None:
        self.embedding_service = embedding_service or EmbeddingService()
        self.driver = get_driver()
        self._schema_ready = False

    def prepare(
        self,
        request: RagQueryRequest,
        *,
        llm_client: RagLlmClient,
        llm_enabled: bool,
    ) -> PreparedConversationMemory:
        summary_json = self._bounded_summary(request.conversationSummaryJson)
        memory_update = self._update_summary(
            summary_json,
            request.evictedMessages,
            llm_client=llm_client,
            llm_enabled=llm_enabled,
        )
        if memory_update and memory_update.updated and memory_update.summaryJson:
            summary_json = memory_update.summaryJson

        recent = self._render_messages(request.recentHistory, self.RECENT_TOKEN_LIMIT)
        relevant_messages = self.retrieve_relevant_turns(request)
        relevant = self._render_messages(relevant_messages, self.RELEVANT_TOKEN_LIMIT)
        return PreparedConversationMemory(summary_json, recent, relevant, memory_update)

    def retrieve_relevant_turns(self, request: RagQueryRequest) -> list[ConversationMessage]:
        if not request.chatSessionId:
            return []
        try:
            self._ensure_schema()
            embedding = self.embedding_service.embed_text(request.question)
            active_ids = self._effective_document_ids(request)
            excluded_ids = [message.messageId for message in request.recentHistory]
            cypher = f"""
            CALL db.index.vector.queryNodes($index_name, 25, $embedding)
            YIELD node, score
            WHERE node.session_id = $session_id
              AND NOT node.turn_id IN $excluded_ids
              AND (
                ($active_document_ids = [] AND size(coalesce(node.active_document_ids, [])) = 0)
                OR (
                  size(coalesce(node.active_document_ids, [])) > 0
                  AND all(document_id IN coalesce(node.active_document_ids, [])
                          WHERE document_id IN $active_document_ids)
                )
              )
            RETURN node.turn_id AS turn_id,
                   node.user_message_id AS user_message_id,
                   node.assistant_message_id AS assistant_message_id,
                   node.user_content AS user_content,
                   node.assistant_content AS assistant_content,
                   node.active_document_ids AS active_document_ids,
                   node.citation_ids AS citation_ids,
                   node.created_at AS created_at,
                   score
            ORDER BY score DESC
            LIMIT $limit
            """
            with self.driver.session() as session:
                records = list(session.run(
                    cypher,
                    index_name=self.VECTOR_INDEX,
                    embedding=embedding,
                    session_id=request.chatSessionId,
                    active_document_ids=active_ids,
                    excluded_ids=excluded_ids,
                    limit=self.RELEVANT_TURN_LIMIT,
                ))
            messages: list[ConversationMessage] = []
            for record in records:
                messages.append(ConversationMessage(
                    messageId=str(record["turn_id"]),
                    role="HISTORICAL_TURN",
                    content=(
                        f"USER: {record['user_content'] or ''}\n"
                        f"ASSISTANT: {record['assistant_content'] or ''}"
                    ),
                    createdAt=record["created_at"],
                    documentIds=list(record["active_document_ids"] or []),
                    citationIds=list(record["citation_ids"] or []),
                ))
            return messages
        except Exception as exc:
            logger.warning("Relevant history retrieval failed for session %s: %s", request.chatSessionId, type(exc).__name__)
            return []

    def index_completed_turn(self, request: RagQueryRequest, answer: str, citation_ids: list[str]) -> None:
        if not request.chatSessionId or not request.currentUserMessageId or not request.currentAssistantMessageId:
            return
        try:
            self._ensure_schema()
            embedding_text = truncate_tokens(f"{request.question}\n{answer}", 1_500)
            embedding = self.embedding_service.embed_text(embedding_text)
            turn_id = f"{request.currentUserMessageId}:{request.currentAssistantMessageId}"
            with self.driver.session() as session:
                session.run(
                    """
                    MERGE (turn:ConversationTurn {turn_id: $turn_id})
                    SET turn.session_id = $session_id,
                        turn.user_message_id = $user_message_id,
                        turn.assistant_message_id = $assistant_message_id,
                        turn.user_content = $user_content,
                        turn.assistant_content = $assistant_content,
                        turn.active_document_ids = $active_document_ids,
                        turn.citation_ids = $citation_ids,
                        turn.embedding = $embedding,
                        turn.created_at = datetime()
                    """,
                    turn_id=turn_id,
                    session_id=request.chatSessionId,
                    user_message_id=request.currentUserMessageId,
                    assistant_message_id=request.currentAssistantMessageId,
                    user_content=truncate_tokens(request.question, 750),
                    assistant_content=truncate_tokens(answer, 1_500),
                    active_document_ids=self._effective_document_ids(request),
                    citation_ids=citation_ids,
                    embedding=embedding,
                ).consume()
        except Exception as exc:
            logger.warning("Conversation turn indexing failed for session %s: %s", request.chatSessionId, type(exc).__name__)

    def _ensure_schema(self) -> None:
        if self._schema_ready:
            return
        dimensions = len(self.embedding_service.embed_text("conversation memory schema"))
        queries = [
            "CREATE CONSTRAINT conversation_turn_id IF NOT EXISTS FOR (n:ConversationTurn) REQUIRE n.turn_id IS UNIQUE",
            f"""
            CREATE VECTOR INDEX {self.VECTOR_INDEX} IF NOT EXISTS
            FOR (n:ConversationTurn) ON (n.embedding)
            OPTIONS {{indexConfig: {{`vector.dimensions`: {dimensions}, `vector.similarity_function`: 'cosine'}}}}
            """,
        ]
        with self.driver.session() as session:
            for query in queries:
                session.run(query).consume()
        self._schema_ready = True

    def _effective_document_ids(self, request: RagQueryRequest) -> list[str]:
        if request.messageAttachedDocumentIds:
            return list(dict.fromkeys(request.messageAttachedDocumentIds))
        if request.focusedDocumentId:
            return [request.focusedDocumentId]
        return list(dict.fromkeys(request.attachedDocumentIds or []))

    def _render_messages(self, messages: list[ConversationMessage], token_limit: int) -> str:
        if not messages:
            return "[none]"
        parts: list[str] = []
        used = 0
        for message in messages:
            remaining = token_limit - used
            if remaining <= 0:
                break
            header = (
                f"[{message.messageId}] role={message.role}; "
                f"historicalCitationIds={','.join(message.citationIds) or '[none]'}; "
                f"documentIds={','.join(message.documentIds) or '[none]'}\n"
            )
            block = header + message.content
            bounded = truncate_tokens(block, remaining)
            parts.append(bounded)
            used += estimate_tokens(bounded)
        return "\n\n".join(parts) or "[none]"

    def _bounded_summary(self, raw_summary: str | None) -> str:
        if not raw_summary:
            return json.dumps(self._empty_summary(), ensure_ascii=False, separators=(",", ":"))
        try:
            parsed = json.loads(raw_summary)
            if not isinstance(parsed, dict):
                raise ValueError("summary must be an object")
            return self._serialize_summary(parsed)
        except Exception:
            logger.warning("Conversation summary JSON is invalid; using empty structured summary")
            return json.dumps(self._empty_summary(), ensure_ascii=False, separators=(",", ":"))

    def _update_summary(
        self,
        existing_summary: str,
        evicted_messages: list[ConversationMessage],
        *,
        llm_client: RagLlmClient,
        llm_enabled: bool,
    ) -> ConversationMemoryUpdate | None:
        if not evicted_messages or not llm_enabled:
            return None
        prompt = (
            "Update the structured conversation memory using ONLY existingSummary + evictedMessages. "
            "Return one JSON object with exactly these keys: conversationGoal, establishedFacts, userRole, "
            "activeDocuments, userConcerns, previousFindings, openQuestions, userPreferences. "
            "Do not treat prior AI conclusions as verified facts. Every previousFindings item must include "
            "finding, documentId, sourceMessageId, citationIds, requiresRevalidation=true. "
            "Do not copy personal data unless needed for the conversation goal.\n\n"
            f"existingSummary={existing_summary}\n"
            f"evictedMessages={json.dumps([message.model_dump() for message in evicted_messages], ensure_ascii=False)}"
        )
        try:
            result = llm_client.generate(
                system_prompt="You maintain compact, privacy-conscious conversation memory. Output JSON only.",
                user_prompt=prompt,
            )
            raw = result.raw_response or result.answer or ""
            parsed = self._parse_json_object(raw)
            if parsed is None:
                return None
            summary_json = self._serialize_summary(parsed)
            return ConversationMemoryUpdate(
                summaryJson=summary_json,
                summarizedThroughMessageId=evicted_messages[-1].messageId,
                updated=True,
            )
        except Exception as exc:
            logger.warning("Incremental conversation summary failed: %s", type(exc).__name__)
            return None

    def _serialize_summary(self, summary: dict) -> str:
        normalized = self._empty_summary()
        for key in normalized:
            if key in summary:
                normalized[key] = summary[key]
        findings = normalized.get("previousFindings")
        if not isinstance(findings, list):
            findings = []
        normalized["previousFindings"] = [
            {
                "finding": str(item.get("finding") or item.get("text") or "")[:800],
                "documentId": item.get("documentId"),
                "sourceMessageId": item.get("sourceMessageId"),
                "citationIds": list(item.get("citationIds") or []),
                "requiresRevalidation": True,
            }
            for item in findings[:20]
            if isinstance(item, dict)
        ]
        serialized = json.dumps(normalized, ensure_ascii=False, separators=(",", ":"))
        if estimate_tokens(serialized) <= self.SUMMARY_TOKEN_LIMIT:
            return serialized
        normalized["previousFindings"] = normalized["previousFindings"][-8:]
        for key in ("establishedFacts", "userConcerns", "openQuestions"):
            if isinstance(normalized.get(key), list):
                normalized[key] = normalized[key][-10:]
        serialized = json.dumps(normalized, ensure_ascii=False, separators=(",", ":"))
        if estimate_tokens(serialized) <= self.SUMMARY_TOKEN_LIMIT:
            return serialized
        logger.warning("Structured conversation summary exceeded budget after compaction; using empty summary")
        return json.dumps(self._empty_summary(), ensure_ascii=False, separators=(",", ":"))

    def _parse_json_object(self, text: str) -> dict | None:
        cleaned = re.sub(r"```(?:json)?|```", "", text, flags=re.IGNORECASE).strip()
        try:
            value = json.loads(cleaned)
            return value if isinstance(value, dict) else None
        except Exception:
            match = re.search(r"\{.*\}", cleaned, flags=re.DOTALL)
            if not match:
                return None
            try:
                value = json.loads(match.group(0))
                return value if isinstance(value, dict) else None
            except Exception:
                return None

    def _empty_summary(self) -> dict:
        return {
            "conversationGoal": None,
            "establishedFacts": [],
            "userRole": None,
            "activeDocuments": [],
            "userConcerns": [],
            "previousFindings": [],
            "openQuestions": [],
            "userPreferences": {},
        }
