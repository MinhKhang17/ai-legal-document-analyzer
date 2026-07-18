package com.analyzer.api.dto.chatmessage;

import com.analyzer.api.enums.FeedbackReason;
import com.analyzer.api.enums.FeedbackRating;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request payload for rating an AI assistant chat message")
public class ChatMessageFeedbackRequest {

    @NotNull(message = "Rating không được để trống")
    @Schema(description = "Thumbs up or down", example = "THUMBS_DOWN")
    private FeedbackRating rating;

    @Schema(description = "Preset lý do, chỉ áp dụng khi rating = THUMBS_DOWN", example = "[\"INCORRECT\", \"INCOMPLETE\"]")
    private List<FeedbackReason> reasons;

    @Size(max = 2000, message = "Comment không được vượt quá 2000 ký tự")
    @Schema(description = "Optional free-text comment, dùng thêm khi chọn lý do OTHER", example = "Câu trả lời trích dẫn sai điều luật")
    private String comment;
}
