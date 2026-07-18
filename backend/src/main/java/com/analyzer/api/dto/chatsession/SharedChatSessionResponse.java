package com.analyzer.api.dto.chatsession;

import com.analyzer.api.dto.chatmessage.ChatMessageResponse;
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
@Schema(description = "Read-only view of a chat session accessed through a share link")
public class SharedChatSessionResponse {

    @Schema(description = "ID of the chat session", example = "chat_001")
    private String chatSessionId;

    @Schema(description = "Title of the chat session", example = "Hỏi đáp về hợp đồng lao động")
    private String title;

    @Schema(description = "Display name of the chat session owner", example = "Nguyen Van A")
    private String ownerName;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp the session was shared")
    private LocalDateTime sharedAt;

    @Schema(description = "Full chronological message history of the session")
    private List<ChatMessageResponse> messages;
}
