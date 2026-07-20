package com.analyzer.api.dto.chatmessage;

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
    private String message;

    @Size(max = 100, message = "Document ID must not exceed 100 characters")
    private String documentId;

    private String focusedDocumentId;

    private List<String> messageAttachedDocumentIds;

    private String userRole;

    private String conversationMode;
}
