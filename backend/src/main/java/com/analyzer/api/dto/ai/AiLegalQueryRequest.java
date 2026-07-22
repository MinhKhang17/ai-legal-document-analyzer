package com.analyzer.api.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contract request sent from the BE to the Python AI service for a legal query.
 * This keeps the existing RAG flow intact while adding ticket-suggestion metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for a legal AI query")
public class AiLegalQueryRequest {

    @JsonProperty("request_id")
    @Schema(description = "Stable correlation id for the AI call")
    private String requestId;

    @NotBlank(message = "user_id khong duoc de trong")
    @JsonProperty("user_id")
    @Schema(description = "Authenticated user id as a string for the AI service")
    private String userId;

    @NotBlank(message = "workspace_id khong duoc de trong")
    @JsonProperty("workspace_id")
    @Schema(description = "Workspace identifier used to scope retrieval")
    private String workspaceId;

    @JsonProperty("document_id")
    @Schema(description = "Optional document identifier when a single document is in focus")
    private String documentId;

    @NotBlank(message = "question khong duoc de trong")
    @JsonProperty("question")
    @Schema(description = "User's legal question")
    private String question;

    @JsonProperty("top_k_checklist")
    @Schema(description = "How many checklist items to inspect first")
    private Integer topKChecklist;

    @JsonProperty("top_k_user_chunks_per_checklist")
    @Schema(description = "How many user-document chunks to fetch per checklist item")
    private Integer topKUserChunksPerChecklist;

    @JsonProperty("top_k_knowledge_chunks")
    @Schema(description = "How many legal knowledge chunks to fetch")
    private Integer topKKnowledgeChunks;
}
