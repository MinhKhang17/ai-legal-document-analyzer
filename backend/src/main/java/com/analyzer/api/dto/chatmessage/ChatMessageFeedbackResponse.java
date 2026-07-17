package com.analyzer.api.dto.chatmessage;

import com.analyzer.api.enums.FeedbackReason;
import com.analyzer.api.enums.FeedbackRating;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Response payload for a chat message feedback entry")
public class ChatMessageFeedbackResponse {

    @Schema(description = "Feedback ID")
    private String id;

    @Schema(description = "ID of the rated chat message")
    private String chatMessageId;

    @Schema(description = "Snippet of the rated AI answer content")
    private String messageContent;

    @Schema(description = "Thumbs up or down")
    private FeedbackRating rating;

    @Schema(description = "Preset lý do được chọn khi rating = THUMBS_DOWN")
    private List<FeedbackReason> reasons;

    @Schema(description = "Feedback comment")
    private String comment;

    @Schema(description = "ID khách hàng đã gửi đánh giá (chủ sở hữu tin nhắn được đánh giá)")
    private Long submittedById;

    @Schema(description = "Tên hiển thị của khách hàng đã gửi đánh giá")
    private String submittedByName;

    @Schema(description = "Timestamp the feedback was submitted")
    private LocalDateTime createdAt;
}
