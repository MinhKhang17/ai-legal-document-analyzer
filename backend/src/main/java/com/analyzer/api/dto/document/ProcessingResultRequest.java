package com.analyzer.api.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
@Schema(description = "Request payload from Python AI service after document processing")
public class ProcessingResultRequest {

    @Schema(description = "Processing job ID", example = "job_001")
    private String jobId;

    @NotBlank(message = "status khong duoc de trong")
    @Schema(description = "Processing status", example = "READY")
    private String status;

    @PositiveOrZero(message = "chunkCount khong duoc am")
    @Schema(description = "Number of chunks created", example = "86")
    private Integer chunkCount;

    @Schema(description = "Error message when processing fails", example = "Cannot extract text from scanned PDF")
    private String errorMessage;
}
