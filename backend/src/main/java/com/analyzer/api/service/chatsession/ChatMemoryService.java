package com.analyzer.api.service.chatsession;

import com.analyzer.api.dto.chatsession.AppendChatContextRequest;
import com.analyzer.api.dto.chatsession.ChatSessionMemoryResponse;
import com.analyzer.api.dto.chatsession.ChatSessionSummaryResponse;

public interface ChatMemoryService {

    ChatSessionSummaryResponse getSummary(String chatSessionId, Long userId);

    ChatSessionMemoryResponse getMemory(String chatSessionId, Long userId);

    ChatSessionMemoryResponse appendContext(String chatSessionId, Long userId, AppendChatContextRequest request);
}
