package com.analyzer.api.dto.chatmessage;

import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
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
    private Double confidenceScore;
    private Boolean shouldSuggestTicket;
    private SuggestionType suggestionType;
    private String suggestionReason;
    private String missingInformation;
    private RiskLevel riskLevel;
    private String legalDomain;
    private UserActionHint userActionHint;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
