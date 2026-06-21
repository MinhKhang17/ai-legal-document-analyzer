package com.analyzer.api.dto.chatsession;

import com.analyzer.api.enums.ChatSessionStatus;
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
@Schema(description = "Response payload for chat session details")
public class ChatSessionResponse {

    @Schema(description = "ID of the chat session", example = "chat_001")
    private String chatSessionId;

    @Schema(description = "ID of the workspace", example = "ws_001")
    private String workspaceId;

    @Schema(description = "Title of the chat session", example = "Hỏi đáp về hợp đồng lao động")
    private String title;

    @Schema(description = "Status of the chat session", example = "ACTIVE")
    private ChatSessionStatus status;

    @Schema(description = "Marks if this is the default chat session of the workspace", example = "false")
    private Boolean isDefault;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "Timestamp of the last message in this session")
    private LocalDateTime lastMessageAt;
}
