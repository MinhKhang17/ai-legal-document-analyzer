package com.analyzer.api.dto.feedback;

import com.analyzer.api.enums.AiReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReportResponse {

    private String id;
    private String reportType;
    private String sourceType;
    private String sourceReferenceId;
    private String summary;
    private String detailsJson;
    private Long submittedById;
    private Long workspaceId;
    private AiReportStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
