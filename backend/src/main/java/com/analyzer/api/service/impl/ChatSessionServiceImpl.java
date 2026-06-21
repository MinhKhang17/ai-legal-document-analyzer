package com.analyzer.api.service.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatsession.ChatSessionResponse;
import com.analyzer.api.dto.chatsession.CreateChatSessionRequest;
import com.analyzer.api.dto.chatsession.UpdateChatSessionRequest;
import com.analyzer.api.dto.chatsession.DeleteChatSessionResponse;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.ChatSessionStatus;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.exception.workspace.WorkspaceDeletedException;
import com.analyzer.api.exception.validation.InvalidPageException;
import com.analyzer.api.exception.validation.InvalidSizeException;
import com.analyzer.api.exception.chat.DeletedChatSessionException;
import com.analyzer.api.exception.chat.InvalidTitleException;
import com.analyzer.api.repository.ChatSessionRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final WorkspaceRepository workspaceRepository;

    @Override
    @Transactional
    public ChatSessionResponse createChatSession(Long userId, String workspaceId, CreateChatSessionRequest request) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));

        if (!workspace.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to access this workspace");
        }

        if ("DELETED".equalsIgnoreCase(workspace.getStatus())) {
            throw new WorkspaceDeletedException(
                    "Workspace is deleted and cannot be used to create chat session",
                    workspaceId,
                    workspace.getStatus());
        }

        ChatSession chatSession = ChatSession.builder()
                .id(generateChatSessionId())
                .user(workspace.getUser())
                .workspace(workspace)
                .title(request.getTitle().trim())
                .status(ChatSessionStatus.ACTIVE)
                .isDefault(false)
                .build();

        ChatSession savedSession = chatSessionRepository.save(chatSession);
        return toResponse(savedSession);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatSessionResponse> getChatSessionsByWorkspace(
            Long userId,
            String workspaceId,
            int page,
            int size,
            ChatSessionStatus status) {
        if (page < 0) {
            throw new InvalidPageException("Page index must not be negative", page);
        }
        if (size <= 0) {
            throw new InvalidSizeException("Page size must be greater than 0", size);
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));

        if (!workspace.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to access this workspace");
        }

        if ("DELETED".equalsIgnoreCase(workspace.getStatus())) {
            throw new WorkspaceDeletedException("Workspace has been deleted", workspaceId, workspace.getStatus());
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "lastMessageAt")
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ChatSession> chatSessionPage = chatSessionRepository.findByWorkspaceIdAndUserIdAndStatus(
                workspaceId,
                userId,
                status,
                pageable);

        List<ChatSessionResponse> items = chatSessionPage.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<ChatSessionResponse>builder()
                .items(items)
                .page(chatSessionPage.getNumber())
                .size(chatSessionPage.getSize())
                .totalItems(chatSessionPage.getTotalElements())
                .totalPages(chatSessionPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessionResponse getChatSessionDetail(Long userId, String chatSessionId) {
        ChatSession chatSession = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        if (!chatSession.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to access this chat session");
        }

        if (chatSession.getStatus() == ChatSessionStatus.DELETED) {
            throw new DeletedChatSessionException("Deleted chat session cannot be accessed", chatSessionId, "DELETED");
        }

        return toResponse(chatSession);
    }

    @Override
    @Transactional
    public ChatSessionResponse updateChatSession(Long userId, String chatSessionId, UpdateChatSessionRequest request) {
        // Validate request title
        if (request == null || request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new InvalidTitleException("Chat session title is required", true, 255);
        }
        if (request.getTitle().length() > 255) {
            throw new InvalidTitleException("Chat session title must not exceed 255 characters", false, 255);
        }

        ChatSession chatSession = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        if (!chatSession.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to update this chat session");
        }

        if (chatSession.getStatus() == ChatSessionStatus.DELETED) {
            throw new DeletedChatSessionException("Deleted chat session cannot be updated", chatSessionId, "DELETED");
        }

        chatSession.setTitle(request.getTitle().trim());
        chatSession.setUpdatedAt(LocalDateTime.now());
        ChatSession savedSession = chatSessionRepository.save(chatSession);

        return toResponse(savedSession);
    }

    @Override
    @Transactional
    public DeleteChatSessionResponse deleteChatSession(Long userId, String chatSessionId) {
        ChatSession chatSession = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        if (!chatSession.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to delete this chat session");
        }

        if (chatSession.getStatus() == ChatSessionStatus.DELETED) {
            throw new DeletedChatSessionException("Chat session is already deleted", chatSessionId, "DELETED");
        }

        chatSession.setStatus(ChatSessionStatus.DELETED);
        chatSession.setUpdatedAt(LocalDateTime.now());
        ChatSession savedSession = chatSessionRepository.save(chatSession);

        return DeleteChatSessionResponse.builder()
                .chatSessionId(savedSession.getId())
                .status(savedSession.getStatus())
                .deletedAt(savedSession.getUpdatedAt())
                .build();
    }

    private String generateChatSessionId() {
        return "chat_" + UUID.randomUUID().toString().replace("-", "");
    }

    private ChatSessionResponse toResponse(ChatSession chatSession) {
        return ChatSessionResponse.builder()
                .chatSessionId(chatSession.getId())
                .workspaceId(chatSession.getWorkspace().getId())
                .title(chatSession.getTitle())
                .status(chatSession.getStatus())
                .isDefault(chatSession.getIsDefault())
                .createdAt(chatSession.getCreatedAt())
                .updatedAt(chatSession.getUpdatedAt())
                .lastMessageAt(chatSession.getLastMessageAt())
                .build();
    }
}
