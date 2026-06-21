package com.analyzer.api.dto.chatmessage;

import com.analyzer.api.dto.chatsession.ChatSessionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageResponse {
    private ChatSessionResponse chatSession;
    private ChatMessageResponse userMessage;
    private ChatMessageResponse assistantMessage;
}
