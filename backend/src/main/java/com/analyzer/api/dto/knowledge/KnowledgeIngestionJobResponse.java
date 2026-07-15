package com.analyzer.api.dto.knowledge;

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
public class KnowledgeIngestionJobResponse {

    private String id;
    private String knowledgeBaseVersionId;
    private String requestId;
    private KnowledgeStatus status;
    private String jobPayload;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
