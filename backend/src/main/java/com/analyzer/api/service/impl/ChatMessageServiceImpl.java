package com.analyzer.api.service.impl;

import com.analyzer.api.client.PythonAiClient;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.ai.RagQueryRequest;
import com.analyzer.api.dto.ai.RagQueryResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageResponse;
import com.analyzer.api.dto.chatmessage.SendMessageRequest;
import com.analyzer.api.dto.chatmessage.SendMessageResponse;
import com.analyzer.api.dto.chatsession.ChatSessionResponse;
import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import com.analyzer.api.enums.ChatMessageType;
import com.analyzer.api.enums.ChatSessionStatus;
import com.analyzer.api.exception.common.*;
import com.analyzer.api.exception.workspace.*;
import com.analyzer.api.exception.chat.*;
import com.analyzer.api.exception.ai.*;
import com.analyzer.api.exception.validation.*;
import com.analyzer.api.repository.ChatMessageRepository;
import com.analyzer.api.repository.ChatSessionRepository;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.service.ChatMessageService;
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
public class ChatMessageServiceImpl implements ChatMessageService {

    private final WorkspaceRepository workspaceRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PythonAiClient pythonAiClient;

    @Override
    @Transactional(noRollbackFor = {AiServiceUnavailableException.class, AiServiceTimeoutException.class})
    public SendMessageResponse sendMessageInWorkspace(Long userId, String workspaceId, SendMessageRequest request) {
        // Validate request
        validateMessageRequest(request);

        // Find Workspace
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));

        // Validate Ownership
        if (!workspace.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to access this workspace");
        }

        // Validate Deleted State
        if ("DELETED".equalsIgnoreCase(workspace.getStatus())) {
            throw new WorkspaceDeletedException("Workspace has been deleted", workspaceId, workspace.getStatus());
        }

        // Validate Documents
        validateWorkspaceDocuments(userId, workspaceId);

        // Find or create default ChatSession
        ChatSession chatSession = chatSessionRepository.findByWorkspaceIdAndUserIdAndIsDefaultTrueAndStatus(
                workspaceId, userId, ChatSessionStatus.ACTIVE)
                .orElseGet(() -> createDefaultChatSession(workspace));

        // Create User Message
        ChatMessage userMessage = createAndSaveUserMessage(chatSession, request.getMessage().trim());

        // Process AI Query
        return executeAiQuery(workspace, chatSession, userMessage, request.getMessage().trim());
    }

    @Override
    @Transactional(noRollbackFor = {AiServiceUnavailableException.class, AiServiceTimeoutException.class})
    public SendMessageResponse sendMessageInChatSession(Long userId, String chatSessionId, SendMessageRequest request) {
        // Validate request
        validateMessageRequest(request);

        // Find ChatSession
        ChatSession chatSession = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        // Validate Ownership
        if (!chatSession.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to access this chat session");
        }

        // Validate Deleted State
        if (chatSession.getStatus() == ChatSessionStatus.DELETED) {
            throw new DeletedChatSessionException("Chat session has been deleted", chatSessionId, "DELETED");
        }

        // Validate Workspace State
        Workspace workspace = chatSession.getWorkspace();
        if ("DELETED".equalsIgnoreCase(workspace.getStatus())) {
            throw new WorkspaceDeletedException("Workspace has been deleted", workspace.getId(), workspace.getStatus());
        }

        // Validate Documents
        validateWorkspaceDocuments(userId, workspace.getId());

        // Create User Message
        ChatMessage userMessage = createAndSaveUserMessage(chatSession, request.getMessage().trim());

        // Process AI Query
        return executeAiQuery(workspace, chatSession, userMessage, request.getMessage().trim());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> getMessagesByChatSession(Long userId, String chatSessionId, int page, int size) {
        // Validate page and size
        if (page < 0) {
            throw new InvalidPageException("Page index must not be negative", page);
        }
        if (size <= 0) {
            throw new InvalidSizeException("Page size must be greater than 0", size);
        }

        // Find ChatSession
        ChatSession chatSession = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        // Validate Ownership
        if (!chatSession.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to access this chat session");
        }

        // Validate Deleted State
        if (chatSession.getStatus() == ChatSessionStatus.DELETED) {
            throw new DeletedChatSessionException("Chat session has been deleted", chatSessionId, "DELETED");
        }

        // Query messages with pagination, sort by createdAt ASC
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "createdAt")
        );

        Page<ChatMessage> messagePage =
                chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(chatSessionId, pageable);

        List<ChatMessageResponse> items = messagePage.getContent().stream()
                .map(this::toChatMessageResponse)
                .toList();

        return PageResponse.<ChatMessageResponse>builder()
                .items(items)
                .page(messagePage.getNumber())
                .size(messagePage.getSize())
                .totalItems(messagePage.getTotalElements())
                .totalPages(messagePage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatMessageResponse getMessageDetail(Long userId, String messageId) {
        // Find ChatMessage
        ChatMessage chatMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        // Validate Ownership
        if (!chatMessage.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to access this message");
        }

        // Check if the ChatSession of this message has been deleted
        ChatSession chatSession = chatMessage.getChatSession();
        if (chatSession.getStatus() == ChatSessionStatus.DELETED) {
            throw new DeletedChatSessionException("Message belongs to a deleted chat session", chatSession.getId(), "DELETED");
        }

        return toChatMessageResponse(chatMessage);
    }

    private void validateMessageRequest(SendMessageRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new InvalidMessageException("Message content is required", true, 5000);
        }
        if (request.getMessage().length() > 5000) {
            throw new InvalidMessageException("Message content must not exceed 5000 characters", false, 5000);
        }
    }

    private void validateWorkspaceDocuments(Long userId, String workspaceId) {
        long readyDocCount = documentRepository.countByWorkspaceIdAndUserIdAndStatus(workspaceId, userId, "READY");
        if (readyDocCount == 0) {
            long processingDocCount = documentRepository.countByWorkspaceIdAndUserIdAndStatusIn(
                    workspaceId, userId, List.of("UPLOADED", "PROCESSING"));
            if (processingDocCount > 0) {
                throw new NoReadyDocumentsException(
                        "Documents are still processing. Please wait until at least one document is ready",
                        workspaceId, 0, processingDocCount);
            } else {
                throw new NoReadyDocumentsException(
                        "Workspace does not have any ready document for chat",
                        workspaceId, 0, 0);
            }
        }
    }

    private ChatSession createDefaultChatSession(Workspace workspace) {
        String chatSessionId = "chat_" + UUID.randomUUID().toString().replace("-", "");
        ChatSession chatSession = ChatSession.builder()
                .id(chatSessionId)
                .user(workspace.getUser())
                .workspace(workspace)
                .title("Default Conversation") // temporary title, updated later or kept simple
                .status(ChatSessionStatus.ACTIVE)
                .isDefault(true)
                .lastMessageAt(LocalDateTime.now())
                .build();
        return chatSessionRepository.save(chatSession);
    }

    private ChatMessage createAndSaveUserMessage(ChatSession chatSession, String content) {
        String userMessageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        ChatMessage userMessage = ChatMessage.builder()
                .id(userMessageId)
                .chatSession(chatSession)
                .user(chatSession.getUser())
                .role(ChatMessageRole.USER)
                .messageType(ChatMessageType.NORMAL_CHAT)
                .content(content)
                .status(ChatMessageStatus.PROCESSING)
                .build();
        return chatMessageRepository.save(userMessage);
    }

    private SendMessageResponse executeAiQuery(Workspace workspace, ChatSession chatSession, ChatMessage userMessage, String question) {
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        userMessage.setRequestId(requestId);

        // Update default chat session title to the first message if needed
        if (chatSession.getIsDefault() && "Default Conversation".equals(chatSession.getTitle())) {
            String title = question;
            if (title.length() > 100) {
                title = title.substring(0, 100);
            }
            chatSession.setTitle(title);
        }

        RagQueryResponse aiResponse;
        try {
            RagQueryRequest aiRequest = RagQueryRequest.builder()
                    .requestId(requestId)
                    .userId(String.valueOf(workspace.getUser().getId()))
                    .workspaceId(workspace.getId())
                    .question(question)
                    .topKChecklist(10)
                    .topKUserChunksPerChecklist(3)
                    .topKKnowledgeChunks(5)
                    .build();
            aiResponse = pythonAiClient.query(aiRequest);
        } catch (AiServiceUnavailableException | AiServiceTimeoutException ex) {
            // Persist failed user message state
            userMessage.setStatus(ChatMessageStatus.FAILED);
            userMessage.setErrorMessage(ex.getMessage());
            chatMessageRepository.save(userMessage);

            // Persist failed assistant message
            String assistantMessageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
            ChatMessage assistantMessage = ChatMessage.builder()
                    .id(assistantMessageId)
                    .chatSession(chatSession)
                    .user(chatSession.getUser())
                    .role(ChatMessageRole.ASSISTANT)
                    .messageType(ChatMessageType.NORMAL_CHAT)
                    .content("AI service is currently unavailable. Please try again later.")
                    .status(ChatMessageStatus.FAILED)
                    .requestId(requestId)
                    .errorMessage(ex.getMessage())
                    .build();
            chatMessageRepository.save(assistantMessage);

            // Update session timestamp
            chatSession.setLastMessageAt(LocalDateTime.now());
            chatSessionRepository.save(chatSession);

            throw ex;
        }

        // AI succeeded - update user message
        userMessage.setStatus(ChatMessageStatus.COMPLETED);
        chatMessageRepository.save(userMessage);

        // Create successful assistant message
        String assistantMessageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        ChatMessage.ChatMessageBuilder assistantBuilder = ChatMessage.builder()
                .id(assistantMessageId)
                .chatSession(chatSession)
                .user(chatSession.getUser())
                .role(ChatMessageRole.ASSISTANT)
                .messageType(ChatMessageType.NORMAL_CHAT)
                .content(aiResponse.getAnswer())
                .status(ChatMessageStatus.COMPLETED)
                .requestId(requestId)
                .aiModel(aiResponse.getModel());

        if (aiResponse.getUsage() != null) {
            assistantBuilder.promptTokens(aiResponse.getUsage().getPromptTokens())
                    .completionTokens(aiResponse.getUsage().getCompletionTokens())
                    .totalTokens(aiResponse.getUsage().getTotalTokens());
        }

        ChatMessage assistantMessage = assistantBuilder.build();
        chatMessageRepository.save(assistantMessage);

        // Update ChatSession timestamps
        chatSession.setLastMessageAt(LocalDateTime.now());
        chatSessionRepository.save(chatSession);

        return SendMessageResponse.builder()
                .chatSession(toChatSessionResponse(chatSession))
                .userMessage(toChatMessageResponse(userMessage))
                .assistantMessage(toChatMessageResponse(assistantMessage))
                .build();
    }

    private ChatSessionResponse toChatSessionResponse(ChatSession chatSession) {
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

    private ChatMessageResponse toChatMessageResponse(ChatMessage message) {
        if (message == null) {
            return null;
        }
        return ChatMessageResponse.builder()
                .messageId(message.getId())
                .chatSessionId(message.getChatSession().getId())
                .role(message.getRole().name())
                .messageType(message.getMessageType().name())
                .content(message.getContent())
                .status(message.getStatus().name())
                .requestId(message.getRequestId())
                .aiModel(message.getAiModel())
                .promptTokens(message.getPromptTokens())
                .completionTokens(message.getCompletionTokens())
                .totalTokens(message.getTotalTokens())
                .errorMessage(message.getErrorMessage())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }
}
