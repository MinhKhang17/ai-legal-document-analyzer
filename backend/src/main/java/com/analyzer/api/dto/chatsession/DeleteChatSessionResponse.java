package com.analyzer.api.dto.chatsession;

import com.analyzer.api.enums.ChatSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteChatSessionResponse {
    private String chatSessionId;
    private ChatSessionStatus status;
    private LocalDateTime deletedAt;
}
