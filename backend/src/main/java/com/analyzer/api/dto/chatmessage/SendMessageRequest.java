package com.analyzer.api.dto.chatmessage;

import com.analyzer.api.enums.ChatMode;
import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message content must not exceed 5000 characters")
    @JsonAlias("query")
    private String message;

    /** Optional body alias for clients using a single unified-query contract. */
    private String sessionId;

    @Size(max = 100, message = "Document ID must not exceed 100 characters")
    private String documentId;

    /** Optional unified-chat document selection. Legacy documentId remains supported. */
    private List<String> documentIds;

    private String focusedDocumentId;

    private List<String> messageAttachedDocumentIds;

    private String userRole;

    private String conversationMode;

    @Builder.Default
    private ChatMode mode = ChatMode.AUTO;
}
