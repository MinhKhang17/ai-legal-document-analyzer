package com.analyzer.api.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseIngestedDocumentVersionResponse {

    private String versionId;
    private String versionLabel;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String visibility;
    private Boolean active;
    private String ingestStatus;
    private Integer chunkCount;
    private Integer embeddedCount;
    private String sourceFileId;
    private String contentHash;
    private LocalDateTime ingestedAt;
    private LocalDateTime publishedAt;
    private Long ingestedById;
    private String errorMessage;
}
