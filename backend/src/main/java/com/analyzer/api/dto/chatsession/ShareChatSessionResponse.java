package com.analyzer.api.dto.chatsession;

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
@Schema(description = "Response payload after sharing a chat session")
public class ShareChatSessionResponse {

    @Schema(description = "ID of the shared chat session", example = "chat_001")
    private String chatSessionId;

    @Schema(description = "Random share token identifying the shared session")
    private String shareToken;

    @Schema(description = "Full read-only share URL for Admin/Expert access")
    private String shareUrl;

    @Schema(description = "Timestamp the session was first shared")
    private LocalDateTime sharedAt;

    private com.analyzer.api.enums.ShareAccessLevel accessLevel;

    private boolean authenticationRequired;
}
