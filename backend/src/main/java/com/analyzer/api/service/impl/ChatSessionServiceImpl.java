package com.analyzer.api.service.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageResponse;
import com.analyzer.api.dto.chatsession.ChatSessionResponse;
import com.analyzer.api.dto.chatsession.CreateChatSessionRequest;
import com.analyzer.api.dto.chatsession.ShareChatSessionResponse;
import com.analyzer.api.dto.chatsession.SharedChatSessionResponse;
import com.analyzer.api.dto.chatsession.UpdateChatSessionRequest;
import com.analyzer.api.dto.chatsession.DeleteChatSessionResponse;
import com.analyzer.api.entity.ChatMessage;
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
import com.analyzer.api.repository.chatmessage.ChatMessageRepository;
import com.analyzer.api.repository.chatsession.ChatSessionRepository;
import com.analyzer.api.repository.workspace.WorkspaceRepository;
import com.analyzer.api.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final ChatMessageRepository chatMessageRepository;

    @Value("${app.mail.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

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

    @Override
    @Transactional
    public ShareChatSessionResponse shareChatSession(Long userId, String chatSessionId,
                                                     com.analyzer.api.enums.ShareAccessLevel accessLevel) {
        ChatSession chatSession = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        if (!chatSession.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to share this chat session");
        }

        if (chatSession.getStatus() == ChatSessionStatus.DELETED) {
            throw new DeletedChatSessionException("Deleted chat session cannot be shared", chatSessionId, "DELETED");
        }

        if (chatSession.getShareToken() == null || chatSession.getShareToken().isBlank()) {
            chatSession.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        }
        if (!Boolean.TRUE.equals(chatSession.getIsShared())) {
            chatSession.setIsShared(true);
            chatSession.setSharedAt(LocalDateTime.now());
        }
        // Product rule: possession of the share URL grants read-only access.
        // Normalize every newly generated/reused link to PUBLIC even if an older
        // client still sends the legacy RESTRICTED value.
        chatSession.setShareAccessLevel(com.analyzer.api.enums.ShareAccessLevel.PUBLIC);

        ChatSession savedSession = chatSessionRepository.save(chatSession);

        return ShareChatSessionResponse.builder()
                .chatSessionId(savedSession.getId())
                .shareToken(savedSession.getShareToken())
                .shareUrl(frontendBaseUrl + "/shared/chat/" + savedSession.getShareToken())
                .sharedAt(savedSession.getSharedAt())
                .accessLevel(savedSession.getShareAccessLevel())
                .authenticationRequired(false)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SharedChatSessionResponse getSharedChatSession(String shareToken) {
        ChatSession chatSession = chatSessionRepository.findByShareTokenAndIsSharedTrue(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Shared chat session not found or no longer shared"));

        // Legacy links may still be stored as RESTRICTED. They are intentionally
        // treated as public so existing share URLs follow the current product rule.
        com.analyzer.api.enums.ShareAccessLevel accessLevel = com.analyzer.api.enums.ShareAccessLevel.PUBLIC;

        List<ChatMessageResponse> messages = chatMessageRepository
                .findByChatSessionIdOrderByCreatedAtAsc(chatSession.getId())
                .stream()
                .map(this::toChatMessageResponse)
                .toList();

        return SharedChatSessionResponse.builder()
                .chatSessionId(chatSession.getId())
                .title(chatSession.getTitle())
                .ownerName(chatSession.getUser().getFirstName() + " " + chatSession.getUser().getLastName())
                .createdAt(chatSession.getCreatedAt())
                .sharedAt(chatSession.getSharedAt())
                .messages(messages)
                .accessLevel(accessLevel)
                .readOnly(true)
                .build();
    }

    private ChatMessageResponse toChatMessageResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .messageId(message.getId())
                .chatSessionId(message.getChatSession().getId())
                .role(message.getRole().name())
                .messageType(message.getMessageType().name())
                .content(message.getContent())
                .status(message.getStatus().name())
                .requestId(message.getRequestId())
                .aiModel(message.getAiModel())
                .confidenceScore(message.getConfidenceScore())
                .shouldSuggestTicket(message.getShouldSuggestTicket())
                .suggestionType(message.getSuggestionType())
                .suggestionReason(message.getSuggestionReason())
                .missingInformation(message.getMissingInformation())
                .riskLevel(message.getRiskLevel())
                .legalDomain(message.getLegalDomain())
                .userActionHint(message.getUserActionHint())
                .promptTokens(message.getPromptTokens())
                .completionTokens(message.getCompletionTokens())
                .totalTokens(message.getTotalTokens())
                .errorMessage(message.getErrorMessage())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
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
