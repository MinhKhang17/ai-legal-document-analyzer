package com.analyzer.api.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Response payload containing document details")
public record DocumentResponseDTO(
        @Schema(description = "Document ID", example = "doc_001")
        String documentId,

        @Schema(description = "Workspace ID", example = "ws_001")
        String workspaceId,

        @Schema(description = "Original file name", example = "hop-dong-lao-dong.pdf")
        String originalFileName,

        @Schema(description = "File MIME type", example = "application/pdf")
        String fileType,

        @Schema(description = "File size in bytes", example = "1240000")
        Long fileSize,

        @Schema(description = "Document status", example = "PROCESSING")
        String status,

        @Schema(description = "Upload date time")
        LocalDateTime uploadedAt
) {
}
