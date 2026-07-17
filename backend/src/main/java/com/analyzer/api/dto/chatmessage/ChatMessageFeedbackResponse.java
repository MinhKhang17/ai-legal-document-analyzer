package com.analyzer.api.dto.chatmessage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload for a chat message feedback entry")
public class ChatMessageFeedbackResponse {

    @Schema(description = "Feedback ID")
    private String id;

    @Schema(description = "ID of the rated chat message")
    private String chatMessageId;

    @Schema(description = "Snippet of the rated AI answer content")
    private String messageContent;

    @Schema(description = "Rating score", example = "5")
    private Integer rating;

    @Schema(description = "Feedback comment")
    private String comment;

    @Schema(description = "Timestamp the feedback was submitted")
    private LocalDateTime createdAt;
}
