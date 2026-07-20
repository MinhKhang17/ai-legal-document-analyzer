package com.analyzer.api.dto.knowledge;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServerBulkIngestFileResponse {
    private String relativePath;
    private String fileHash;
    private String status;
    private String backendMetadataStatus;
    private String postgresStatus;
    private String neo4jStatus;
    private String knowledgeBaseEntryId;
    private String knowledgeBaseVersionId;
    private String jobId;
    private String neo4jDocumentId;
    private Integer chunkCount;
    private String errorMessage;
}
