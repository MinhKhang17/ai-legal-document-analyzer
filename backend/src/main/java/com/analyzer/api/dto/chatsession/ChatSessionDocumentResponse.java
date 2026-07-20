package com.analyzer.api.dto.chatsession;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatSessionDocumentResponse {
    private String mappingId;
    private String chatSessionId;
    private String documentId;
    private String originalFileName;
    private String contentType;
    private Long size;
    private String uploadStatus;
    private LocalDateTime attachedAt;
    private boolean active;
}
