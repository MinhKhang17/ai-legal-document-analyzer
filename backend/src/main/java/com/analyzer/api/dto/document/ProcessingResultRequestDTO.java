package com.analyzer.api.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request payload from Python AI service after document processing")
public class ProcessingResultRequestDTO {

    @Schema(description = "Processing job ID", example = "job_001")
    private String jobId;

    @Schema(description = "Processing status", example = "READY")
    private String status;

    @Schema(description = "Number of chunks created", example = "86")
    private Integer chunkCount;

    @Schema(description = "Error message when processing fails", example = "Cannot extract text from scanned PDF")
    private String errorMessage;
}
