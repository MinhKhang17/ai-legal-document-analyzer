package com.analyzer.api.service.chatsession.impl;

import com.analyzer.api.dto.chatsession.AppendChatContextRequest;
import com.analyzer.api.dto.chatsession.ChatSessionMemoryResponse;
import com.analyzer.api.dto.chatsession.ChatSessionSummaryResponse;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.ChatSessionRepository;
import com.analyzer.api.service.chatsession.ChatMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatMemoryServiceImpl implements ChatMemoryService {

    private final ChatSessionRepository chatSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public ChatSessionSummaryResponse getSummary(String chatSessionId, Long userId) {
        ChatSession session = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Phiên hội thoại ID: " + chatSessionId));

        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập phiên hội thoại này");
        }

        return ChatSessionSummaryResponse.builder()
                .chatSessionId(session.getId())
                .summary(session.getSummary())
                .summaryUpdatedAt(session.getSummaryUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessionMemoryResponse getMemory(String chatSessionId, Long userId) {
        ChatSession session = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Phiên hội thoại ID: " + chatSessionId));

        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập phiên hội thoại này");
        }

        return ChatSessionMemoryResponse.builder()
                .chatSessionId(session.getId())
                .memoryJson(session.getMemoryJson())
                .contextJson(session.getContextJson())
                .contextVersion(session.getContextVersion() != null ? session.getContextVersion() : 1L)
                .build();
    }

    @Override
    @Transactional
    public ChatSessionMemoryResponse appendContext(String chatSessionId, Long userId, AppendChatContextRequest request) {
        ChatSession session = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Phiên hội thoại ID: " + chatSessionId));

        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập phiên hội thoại này");
        }

        session.setContextJson(request.getContextJson());
        long currentVersion = session.getContextVersion() != null ? session.getContextVersion() : 0L;
        session.setContextVersion(currentVersion + 1);
        session.setUpdatedAt(LocalDateTime.now());

        ChatSession savedSession = chatSessionRepository.save(session);

        return ChatSessionMemoryResponse.builder()
                .chatSessionId(savedSession.getId())
                .memoryJson(savedSession.getMemoryJson())
                .contextJson(savedSession.getContextJson())
                .contextVersion(savedSession.getContextVersion())
                .build();
    }
}
