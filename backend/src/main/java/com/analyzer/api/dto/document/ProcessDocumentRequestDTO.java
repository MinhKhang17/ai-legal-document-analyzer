package com.analyzer.api.dto.document;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessDocumentRequestDTO {
    private String jobId;
    private String documentId;
    private String workspaceId;
    private String userId;
    private String sourceType;
    private String fileName;
    private String fileType;
    private String filePath;
    private String callbackUrl;
}
