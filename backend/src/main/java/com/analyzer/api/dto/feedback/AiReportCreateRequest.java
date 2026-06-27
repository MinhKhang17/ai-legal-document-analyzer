package com.analyzer.api.dto.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReportCreateRequest {

    @NotBlank
    private String reportType;

    @NotBlank
    private String sourceType;

    @NotBlank
    private String sourceReferenceId;

    @NotBlank
    private String summary;

    private String detailsJson;

    @NotNull
    private Long submittedById;

    private Long workspaceId;
}
