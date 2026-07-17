package com.analyzer.api.dto.chatmessage;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request payload for rating an AI assistant chat message")
public class ChatMessageFeedbackRequest {

    @NotNull(message = "Rating không được để trống")
    @Min(value = 1, message = "Rating tối thiểu là 1")
    @Max(value = 5, message = "Rating tối đa là 5")
    @Schema(description = "Rating score", example = "5")
    private Integer rating;

    @Size(max = 2000, message = "Comment không được vượt quá 2000 ký tự")
    @Schema(description = "Optional feedback comment", example = "Trực quan và dễ hiểu!")
    private String comment;
}
