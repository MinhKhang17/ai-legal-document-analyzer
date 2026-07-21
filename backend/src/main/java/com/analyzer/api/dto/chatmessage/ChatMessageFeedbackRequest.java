package com.analyzer.api.dto.chatmessage;

import com.analyzer.api.enums.AiFeedbackType;
import com.analyzer.api.enums.FeedbackReason;
import com.analyzer.api.enums.FeedbackRating;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Feedback for an AI assistant chat message")
public class ChatMessageFeedbackRequest {

    @Schema(description = "Unified feedback type", example = "LIKE")
    private AiFeedbackType feedbackType;

    @Size(max = 2000, message = "Reason must not exceed 2000 characters")
    private String reason;

    /** Backward-compatible aliases used by existing clients. */
    @Schema(description = "Legacy thumbs rating", example = "THUMBS_DOWN")
    private FeedbackRating rating;
    private List<FeedbackReason> reasons;

    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    private String comment;
}
