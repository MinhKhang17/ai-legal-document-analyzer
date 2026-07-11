package com.analyzer.api.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDocumentRequestDTO {
    private String workspaceId;
    private String userId;
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private Long fileSize;
}
