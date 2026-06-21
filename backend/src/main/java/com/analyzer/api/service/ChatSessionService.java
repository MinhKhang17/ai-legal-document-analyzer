package com.analyzer.api.service;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatsession.CreateChatSessionRequest;
import com.analyzer.api.dto.chatsession.ChatSessionResponse;
import com.analyzer.api.dto.chatsession.UpdateChatSessionRequest;
import com.analyzer.api.dto.chatsession.DeleteChatSessionResponse;
import com.analyzer.api.enums.ChatSessionStatus;

public interface ChatSessionService {
    ChatSessionResponse createChatSession(Long userId, String workspaceId, CreateChatSessionRequest request);
    PageResponse<ChatSessionResponse> getChatSessionsByWorkspace(Long userId, String workspaceId, int page, int size, ChatSessionStatus status);
    ChatSessionResponse getChatSessionDetail(Long userId, String chatSessionId);
    ChatSessionResponse updateChatSession(Long userId, String chatSessionId, UpdateChatSessionRequest request);
    DeleteChatSessionResponse deleteChatSession(Long userId, String chatSessionId);
}
