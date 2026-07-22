package com.analyzer.api.dto.workspace;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload for creating a workspace")
public record WorkspaceRequest(
        @NotBlank(message = "Tên workspace không được để trống")
        @Schema(description = "Workspace name", example = "Hợp đồng lao động Công ty ABC")
        String name,

        @Schema(description = "Workspace description", example = "Workspace dùng để phân tích hợp đồng lao động")
        String description
) {
}
