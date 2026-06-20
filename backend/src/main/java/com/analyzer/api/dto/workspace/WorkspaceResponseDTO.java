package com.analyzer.api.dto.workspace;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Response payload containing workspace details")
public class WorkspaceResponseDTO {

    @Schema(description = "Workspace ID", example = "ws_001")
    private String workspaceId;

    @Schema(description = "Workspace name", example = "Hợp đồng lao động Công ty ABC")
    private String name;

    @Schema(description = "Workspace description", example = "Workspace dùng để phân tích hợp đồng lao động")
    private String description;

    @Schema(description = "Workspace status", example = "ACTIVE")
    private String status;

    @Schema(description = "Creation date time")
    private LocalDateTime createdAt;
}
