package com.analyzer.api.dto.chatsession;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for creating a chat session")
public class CreateChatSessionRequest {

    @NotBlank(message = "Chat session title is required")
    @Size(max = 255, message = "Chat session title must not exceed 255 characters")
    @Schema(description = "Title of the chat session", example = "Hỏi đáp về hợp đồng lao động")
    private String title;
}
