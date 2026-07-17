package com.analyzer.api.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
