package com.analyzer.api.service.chatsession.impl;

import com.analyzer.api.dto.chatsession.ChatSessionDocumentResponse;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.entity.ChatSessionDocument;
import com.analyzer.api.entity.Document;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.ChatSessionDocumentRepository;
import com.analyzer.api.repository.ChatSessionRepository;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.service.chatsession.ChatSessionDocumentService;
import com.analyzer.api.service.SubscriptionQuotaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionDocumentServiceImpl implements ChatSessionDocumentService {
    private final ChatSessionRepository chatSessionRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionDocumentRepository mappingRepository;
    private final SubscriptionQuotaService subscriptionQuotaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessionDocumentResponse> list(Long userId, String sessionId) {
        requireOwnedSession(userId, sessionId);
        return mappingRepository.findByChatSessionIdAndUserIdAndActiveTrueOrderByAttachedAtAsc(sessionId, userId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ChatSessionDocumentResponse attach(Long userId, String sessionId, String documentId) {
        ChatSession session = requireOwnedSession(userId, sessionId);
        boolean alreadyActive = mappingRepository.findByChatSessionIdAndDocumentIdAndUserId(sessionId, documentId, userId)
                .map(ChatSessionDocument::isActive).orElse(false);
        if (!alreadyActive) {
            subscriptionQuotaService.checkCanAttachDocument(session.getUser(), sessionId);
        }
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("DOCUMENT_NOT_FOUND"));
        if (document.getUser() == null || !userId.equals(document.getUser().getId())
                || document.getWorkspace() == null || !session.getWorkspace().getId().equals(document.getWorkspace().getId())) {
            throw new ForbiddenException("DOCUMENT_ACCESS_DENIED");
        }
        if ("DELETED".equalsIgnoreCase(document.getStatus())) {
            throw new ResourceNotFoundException("DOCUMENT_NOT_FOUND");
        }
        ChatSessionDocument mapping = mappingRepository
                .findByChatSessionIdAndDocumentIdAndUserId(sessionId, documentId, userId)
                .orElseGet(() -> ChatSessionDocument.builder()
                        .chatSession(session).document(document).user(session.getUser()).build());
        mapping.setActive(true);
        ChatSessionDocument saved = mappingRepository.save(mapping);
        syncActiveDocumentState(session, userId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void detach(Long userId, String sessionId, String documentId) {
        requireOwnedSession(userId, sessionId);
        ChatSessionDocument mapping = mappingRepository
                .findByChatSessionIdAndDocumentIdAndUserId(sessionId, documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSION_DOCUMENT_NOT_FOUND"));
        mapping.setActive(false);
        mappingRepository.save(mapping);
        syncActiveDocumentState(mapping.getChatSession(), userId);
    }

    private ChatSession requireOwnedSession(Long userId, String sessionId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CHAT_SESSION_NOT_FOUND"));
    }

    private ChatSessionDocumentResponse toResponse(ChatSessionDocument mapping) {
        Document document = mapping.getDocument();
        return ChatSessionDocumentResponse.builder()
                .mappingId(mapping.getId()).chatSessionId(mapping.getChatSession().getId())
                .documentId(document.getId()).originalFileName(document.getOriginalFileName())
                .contentType(document.getFileType()).size(document.getFileSize())
                .uploadStatus(document.getStatus()).attachedAt(mapping.getAttachedAt()).active(mapping.isActive()).build();
    }

    private void syncActiveDocumentState(ChatSession session, Long userId) {
        List<String> activeDocumentIds = mappingRepository
                .findByChatSessionIdAndUserIdAndActiveTrueOrderByAttachedAtAsc(session.getId(), userId)
                .stream().map(mapping -> mapping.getDocument().getId()).distinct().toList();
        try {
            session.setActiveDocumentIdsJson(objectMapper.writeValueAsString(activeDocumentIds));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize active document state", exception);
        }
        if (session.getFocusedDocumentId() != null && !activeDocumentIds.contains(session.getFocusedDocumentId())) {
            session.setFocusedDocumentId(null);
        }
        chatSessionRepository.save(session);
    }
}
