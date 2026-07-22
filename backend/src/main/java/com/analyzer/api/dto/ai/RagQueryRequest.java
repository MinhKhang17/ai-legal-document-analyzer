package com.analyzer.api.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryRequest {

    @NotBlank(message = "request_id khong duoc de trong")
    @JsonProperty("request_id")
    private String requestId;

    @NotBlank(message = "user_id khong duoc de trong")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "workspace_id khong duoc de trong")
    @JsonProperty("workspace_id")
    private String workspaceId;

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("attached_document_ids")
    private List<String> attachedDocumentIds;

    @JsonProperty("chat_session_id")
    private String chatSessionId;

    @NotBlank(message = "question khong duoc de trong")
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

    @JsonProperty("drafting_action")
    private String draftingAction;

    @JsonProperty("drafting_contract_type")
    private String draftingContractType;

    @JsonProperty("drafting_information")
    private Map<String, String> draftingInformation;

    @JsonProperty("drafting_original_requirement")
    private String draftingOriginalRequirement;

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
