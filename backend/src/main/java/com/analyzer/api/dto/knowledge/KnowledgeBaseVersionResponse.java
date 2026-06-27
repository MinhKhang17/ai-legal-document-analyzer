package com.analyzer.api.dto.knowledge;

import com.analyzer.api.enums.KnowledgeReviewDecision;
import com.analyzer.api.enums.KnowledgeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseVersionResponse {

    private String id;
    private String knowledgeBaseEntryId;
    private Integer versionNo;
    private String sourceDocumentId;
    private String rawContent;
    private String extractedContent;
    private KnowledgeStatus status;
    private KnowledgeReviewDecision reviewDecision;
    private Long reviewedById;
    private LocalDateTime reviewedAt;
    private Long publishedById;
    private LocalDateTime publishedAt;
    private Long archivedById;
    private LocalDateTime archivedAt;
    private String failedReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
