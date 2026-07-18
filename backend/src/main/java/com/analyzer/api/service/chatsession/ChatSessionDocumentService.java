package com.analyzer.api.service.chatsession;

import com.analyzer.api.dto.chatsession.ChatSessionDocumentResponse;

import java.util.List;

public interface ChatSessionDocumentService {
    List<ChatSessionDocumentResponse> list(Long userId, String sessionId);
    ChatSessionDocumentResponse attach(Long userId, String sessionId, String documentId);
    void detach(Long userId, String sessionId, String documentId);
}
