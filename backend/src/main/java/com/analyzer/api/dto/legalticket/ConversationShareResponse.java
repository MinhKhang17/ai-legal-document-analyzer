package com.analyzer.api.dto.legalticket;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ConversationShareResponse {
    private String id;
    private String ticketId;
    private String shareUrl;
    private String scope;
    private String accessMode;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
}
