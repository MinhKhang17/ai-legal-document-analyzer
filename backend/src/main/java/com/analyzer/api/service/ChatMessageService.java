package com.analyzer.api.service;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackRequest;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageResponse;
import com.analyzer.api.dto.chatmessage.SendMessageRequest;
import com.analyzer.api.dto.chatmessage.SendMessageResponse;

public interface ChatMessageService {

    SendMessageResponse sendMessageInWorkspace(Long userId, String workspaceId, SendMessageRequest request);

    SendMessageResponse sendMessageInChatSession(Long userId, String chatSessionId, SendMessageRequest request);

    PageResponse<ChatMessageResponse> getMessagesByChatSession(Long userId, String chatSessionId, int page, int size);

    ChatMessageResponse getMessageDetail(Long userId, String messageId);

    ChatMessageFeedbackResponse submitFeedback(Long userId, String messageId, ChatMessageFeedbackRequest request);

    void removeFeedback(Long userId, String messageId);
}
