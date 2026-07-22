package com.analyzer.api.dto.knowledge;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeIngestionProgressRequest {
    @NotBlank(message = "status khong duoc de trong")
    private String status;

    @Min(value = 0, message = "progressPercent phai tu 0 den 100")
    @Max(value = 100, message = "progressPercent phai tu 0 den 100")
    private Integer progressPercent;

    @Min(value = 0, message = "chunkCount khong duoc am")
    private Integer chunkCount;

    @JsonAlias({"aiDocumentId", "documentId", "neo4j_document_id"})
    private String neo4jDocumentId;
    private String errorMessage;
}
