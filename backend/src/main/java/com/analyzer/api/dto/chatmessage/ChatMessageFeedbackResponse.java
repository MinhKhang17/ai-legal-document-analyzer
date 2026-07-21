package com.analyzer.api.dto.chatmessage;

import com.analyzer.api.enums.AiFeedbackType;
import com.analyzer.api.enums.ChatMode;
import com.analyzer.api.enums.FeedbackReason;
import com.analyzer.api.enums.FeedbackRating;
import com.analyzer.api.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageFeedbackResponse {
    private String id;
    private String messageId;
    private String chatMessageId;
    private String chatSessionId;
    private AiFeedbackType feedbackType;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Existing response fields remain available for older clients.
    private String messageContent;
    private FeedbackRating rating;
    private List<FeedbackReason> reasons;
    private String comment;
    private Long submittedById;
    private String submittedByName;

    // Admin-only presentation metadata.
    private String userEmail;
    private String questionSnippet;
    private String answerSnippet;
    private ChatMode resolvedMode;
    private RiskLevel riskLevel;
    private Long sourceCount;
}
