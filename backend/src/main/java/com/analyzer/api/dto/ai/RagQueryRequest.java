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

    @JsonProperty("question")
    private String question;

    @JsonProperty("top_k_checklist")
    private Integer topKChecklist;

    @JsonProperty("top_k_user_chunks_per_checklist")
    private Integer topKUserChunksPerChecklist;

    @JsonProperty("top_k_knowledge_chunks")
    private Integer topKKnowledgeChunks;
}
