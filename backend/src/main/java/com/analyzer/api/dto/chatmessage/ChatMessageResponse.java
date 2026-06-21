package com.analyzer.api.dto.chatmessage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String messageId;
    private String chatSessionId;
    private String role;
    private String messageType;
    private String content;
    private String status;
    private String requestId;
    private String aiModel;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
