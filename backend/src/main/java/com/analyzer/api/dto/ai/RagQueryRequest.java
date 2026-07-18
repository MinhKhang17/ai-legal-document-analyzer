package com.analyzer.api.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryRequest {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("workspace_id")
    private String workspaceId;

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("attached_document_ids")
    private List<String> attachedDocumentIds;

    @JsonProperty("chat_session_id")
    private String chatSessionId;

    @JsonProperty("question")
    private String question;

    @JsonProperty("top_k_user_chunks")
    private Integer topKUserChunks;

    @JsonProperty("top_k_knowledge_chunks")
    private Integer topKKnowledgeChunks;

    @JsonProperty("chat_history")
    private String chatHistory;

    @JsonProperty("conversation_summary_json")
    private String conversationSummaryJson;

    @JsonProperty("recent_history")
    private List<ConversationMessage> recentHistory;

    @JsonProperty("evicted_messages")
    private List<ConversationMessage> evictedMessages;

    @JsonProperty("current_user_message_id")
    private String currentUserMessageId;

    @JsonProperty("current_assistant_message_id")
    private String currentAssistantMessageId;

    @JsonProperty("focused_document_id")
    private String focusedDocumentId;

    @JsonProperty("message_attached_document_ids")
    private List<String> messageAttachedDocumentIds;

    @JsonProperty("conversation_user_role")
    private String conversationUserRole;

    @JsonProperty("conversation_mode")
    private String conversationMode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMessage {
        private String messageId;
        private String role;
        private String content;
        private String createdAt;
        private List<String> documentIds;
        private List<String> citationIds;
    }
}
