package com.analyzer.api.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Response payload containing document details")
public class DocumentResponseDTO {

    @Schema(description = "Document ID", example = "doc_001")
    private String documentId;

    @Schema(description = "Workspace ID", example = "ws_001")
    private String workspaceId;

    @Schema(description = "Original file name", example = "hop-dong-lao-dong.pdf")
    private String originalFileName;

    @Schema(description = "File MIME type", example = "application/pdf")
    private String fileType;

    @Schema(description = "File size in bytes", example = "1240000")
    private Long fileSize;

    @Schema(description = "Document status", example = "PROCESSING")
    private String status;

    @Schema(description = "Upload date time")
    private LocalDateTime uploadedAt;
}
