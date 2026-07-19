package com.analyzer.api.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeIngestionProgressRequest {
    private String status;
    private Integer progressPercent;
    private Integer chunkCount;
    private String errorMessage;
}
