package com.analyzer.api.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class Neo4jKnowledgeRepairRequest {
    @NotBlank
    private String neo4jDocumentId;
    @Size(max = 64)
    private String fileHash;
    @Size(max = 255)
    private String fileName;
    @Size(max = 255)
    private String title;
    @Size(max = 100)
    private String code;
    @Size(max = 255)
    private String category;
    @Size(max = 100)
    private String source;
    private Integer chunkCount;
    private boolean dryRun;
}
