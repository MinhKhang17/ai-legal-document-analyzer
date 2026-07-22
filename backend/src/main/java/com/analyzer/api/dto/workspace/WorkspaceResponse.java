package com.analyzer.api.dto.workspace;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Response payload containing workspace details")
public record WorkspaceResponse(
        @Schema(description = "Workspace ID", example = "ws_001")
        String workspaceId,

        @Schema(description = "Workspace name", example = "Hợp đồng lao động Công ty ABC")
        String name,

        @Schema(description = "Workspace description", example = "Workspace dùng để phân tích hợp đồng lao động")
        String description,

        @Schema(description = "Workspace status", example = "ACTIVE")
        String status,

        @Schema(description = "Creation date time")
        LocalDateTime createdAt
) {
}
