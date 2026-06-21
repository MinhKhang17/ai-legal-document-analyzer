package com.analyzer.api.dto.chatsession;

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
public class UpdateChatSessionRequest {

    @NotBlank(message = "Chat session title is required")
    @Size(max = 255, message = "Chat session title must not exceed 255 characters")
    private String title;
}
