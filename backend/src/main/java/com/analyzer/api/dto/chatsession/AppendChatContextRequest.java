package com.analyzer.api.dto.chatsession;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppendChatContextRequest {

    @NotNull
    private Long userId;

    @NotBlank
    private String contextJson;
}
