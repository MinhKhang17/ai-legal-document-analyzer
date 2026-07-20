package com.analyzer.api.dto.knowledge;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class KnowledgeIngestionProgressRequest {
    private String status;
    private Integer progressPercent;
    private Integer chunkCount;
    @JsonAlias({"aiDocumentId", "documentId", "neo4j_document_id"})
    private String neo4jDocumentId;
    private String errorMessage;
}
