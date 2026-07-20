package com.analyzer.api.dto.legalticket;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketAttachmentResponse {
    private String id;
    private String originalFileName;
    private String mimeType;
    private Long sizeBytes;
    private String scanStatus;
    private String uploadStatus;
    private LocalDateTime createdAt;
    private String downloadUrl;
}
