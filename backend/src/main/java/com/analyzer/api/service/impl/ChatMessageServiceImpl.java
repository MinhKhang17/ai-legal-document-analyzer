package com.analyzer.api.service.impl;

import com.analyzer.api.client.PythonAiClient;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.ai.RagQueryRequest;
import com.analyzer.api.dto.ai.RagQueryResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackRequest;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageResponse;
import com.analyzer.api.dto.chatmessage.SendMessageRequest;
import com.analyzer.api.dto.chatmessage.SendMessageResponse;
import com.analyzer.api.dto.chatsession.ChatSessionResponse;
import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.entity.ChatMessageFeedback;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.entity.AiCitation;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import com.analyzer.api.enums.ChatMessageType;
import com.analyzer.api.enums.ChatSessionStatus;
import com.analyzer.api.enums.CitationSourceType;
import com.analyzer.api.enums.FeedbackReason;
import com.analyzer.api.enums.FeedbackRating;
import com.analyzer.api.exception.common.*;
import com.analyzer.api.exception.workspace.*;
import com.analyzer.api.exception.chat.*;
import com.analyzer.api.exception.ai.*;
import com.analyzer.api.exception.validation.*;
import com.analyzer.api.repository.ChatMessageFeedbackRepository;
import com.analyzer.api.repository.ChatMessageRepository;
import com.analyzer.api.repository.ChatSessionRepository;
import com.analyzer.api.repository.ChatSessionDocumentRepository;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.repository.ai.AiCitationRepository;
import com.analyzer.api.service.ChatMessageService;
import com.analyzer.api.service.SubscriptionQuotaService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageServiceImpl.class);

    private final WorkspaceRepository workspaceRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PythonAiClient pythonAiClient;
    private final UserRepository userRepository;
    private final SubscriptionQuotaService subscriptionQuotaService;
    private final AiCitationRepository aiCitationRepository;
    private final ChatMessageFeedbackRepository chatMessageFeedbackRepository;
    private final ChatSessionDocumentRepository chatSessionDocumentRepository;

    @Override
    @Transactional(noRollbackFor = {AiServiceUnavailableException.class, AiServiceTimeoutException.class})
    public SendMessageResponse sendMessageInWorkspace(Long userId, String workspaceId, SendMessageRequest request) {
        // Validate request
        validateMessageRequest(request);
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        subscriptionQuotaService.checkCanUseAiChat(currentUser, estimateTokens(request.getMessage()));

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

        // An explicitly selected document remains supported for legacy clients. When no
        // document is selected, the request continues in system-KB-only mode.
        Document selectedDocument = resolveSelectedDocument(userId, workspaceId, request.getDocumentId());

        // Find or create default ChatSession
        ChatSession chatSession = chatSessionRepository.findByWorkspaceIdAndUserIdAndIsDefaultTrueAndStatus(
                workspaceId, userId, ChatSessionStatus.ACTIVE)
                .orElseGet(() -> createDefaultChatSession(workspace));

        // Create User Message
        ChatMessage userMessage = createAndSaveUserMessage(chatSession, request.getMessage().trim());

        // Process AI Query
        return executeAiQuery(currentUser, workspace, chatSession, userMessage, request.getMessage().trim(), selectedDocument);
    }

    @Override
    @Transactional(noRollbackFor = {AiServiceUnavailableException.class, AiServiceTimeoutException.class})
    public SendMessageResponse sendMessageInChatSession(Long userId, String chatSessionId, SendMessageRequest request) {
        // Validate request
        validateMessageRequest(request);
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        subscriptionQuotaService.checkCanUseAiChat(currentUser, estimateTokens(request.getMessage()));

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

        // No attached document means system-KB-only mode. An explicit document ID is
        // still validated to preserve compatibility with older API clients.
        Document selectedDocument = resolveSelectedDocument(userId, workspace.getId(), request.getDocumentId());

        // Create User Message
        ChatMessage userMessage = createAndSaveUserMessage(chatSession, request.getMessage().trim());

        // Process AI Query
        return executeAiQuery(currentUser, workspace, chatSession, userMessage, request.getMessage().trim(), selectedDocument);
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

    @Override
    @Transactional
    public ChatMessageFeedbackResponse submitFeedback(Long userId, String messageId, ChatMessageFeedbackRequest request) {
        ChatMessage chatMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        if (!chatMessage.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to rate this message");
        }

        if (chatMessage.getRole() != ChatMessageRole.ASSISTANT) {
            throw new ForbiddenException("Only AI assistant messages can be rated");
        }

        ChatMessageFeedback feedback = chatMessageFeedbackRepository.findByChatMessageId(messageId)
                .orElseGet(() -> ChatMessageFeedback.builder().chatMessage(chatMessage).build());
        feedback.setRating(request.getRating());
        feedback.setReasons(request.getRating() == FeedbackRating.THUMBS_DOWN
                ? joinReasons(request.getReasons())
                : null);
        feedback.setComment(request.getComment());

        ChatMessageFeedback saved = chatMessageFeedbackRepository.save(feedback);
        return toChatMessageFeedbackResponse(saved);
    }

    private static String joinReasons(List<FeedbackReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return null;
        }
        return reasons.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static List<FeedbackReason> splitReasons(String reasons) {
        if (reasons == null || reasons.isBlank()) {
            return List.of();
        }
        return Arrays.stream(reasons.split(","))
                .map(FeedbackReason::valueOf)
                .collect(Collectors.toList());
    }

    private ChatMessageFeedbackResponse toChatMessageFeedbackResponse(ChatMessageFeedback feedback) {
        String content = feedback.getChatMessage().getContent();
        User submittedBy = feedback.getChatMessage().getUser();
        return ChatMessageFeedbackResponse.builder()
                .id(feedback.getId())
                .chatMessageId(feedback.getChatMessage().getId())
                .messageContent(content != null && content.length() > 200 ? content.substring(0, 200) + "..." : content)
                .rating(feedback.getRating())
                .reasons(splitReasons(feedback.getReasons()))
                .comment(feedback.getComment())
                .submittedById(submittedBy.getId())
                .submittedByName(submittedBy.getFirstName() + " " + submittedBy.getLastName())
                .createdAt(feedback.getCreatedAt())
                .build();
    }

    private void validateMessageRequest(SendMessageRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new InvalidMessageException("Message content is required", true, 5000);
        }
        if (request.getMessage().length() > 5000) {
            throw new InvalidMessageException("Message content must not exceed 5000 characters", false, 5000);
        }
        if (request.getDocumentId() != null && request.getDocumentId().trim().length() > 100) {
            throw new InvalidMessageException("Document ID must not exceed 100 characters", false, 100);
        }
    }

    private void validateWorkspaceDocuments(Long userId, String workspaceId, Document selectedDocument) {
        if (selectedDocument != null) {
            return;
        }

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

    private Document resolveSelectedDocument(Long userId, String workspaceId, String documentId) {
        if (documentId == null || documentId.trim().isEmpty()) {
            return null;
        }

        Document document = documentRepository.findById(documentId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getWorkspace().getId().equals(workspaceId)) {
            throw new ForbiddenException("Document does not belong to the selected workspace");
        }
        if (!document.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to access this document");
        }
        if (!"READY".equalsIgnoreCase(document.getStatus())) {
            throw new NoReadyDocumentsException(
                    "Selected document is not ready for chat",
                    workspaceId,
                    1,
                    0);
        }

        return document;
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

    private SendMessageResponse executeAiQuery(
            User currentUser,
            Workspace workspace,
            ChatSession chatSession,
            ChatMessage userMessage,
            String question,
            Document selectedDocument) {
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

        // Build chat history string
        List<ChatMessage> historyMessages = chatMessageRepository.findByChatSessionIdAndStatusOrderByCreatedAtAsc(
                chatSession.getId(),
                ChatMessageStatus.COMPLETED
        );
        StringBuilder historyBuilder = new StringBuilder();
        for (ChatMessage msg : historyMessages) {
            historyBuilder.append(msg.getRole().name()).append(": ").append(msg.getContent()).append("\n\n");
        }
        String chatHistoryStr = historyBuilder.toString().trim();

        RagQueryResponse aiResponse;
        try {
            var activeMappings = chatSessionDocumentRepository
                    .findByChatSessionIdAndUserIdAndActiveTrueOrderByAttachedAtAsc(chatSession.getId(), currentUser.getId());
            List<String> attachedDocumentIds = activeMappings.stream()
                    .map(mapping -> mapping.getDocument())
                    .filter(document -> "READY".equalsIgnoreCase(document.getStatus()))
                    .map(Document::getId)
                    .distinct()
                    .toList();
            if (!activeMappings.isEmpty() && attachedDocumentIds.isEmpty()) {
                throw new NoReadyDocumentsException(
                        "Attached documents are still processing",
                        workspace.getId(), 0, activeMappings.size());
            }
            RagQueryRequest aiRequest = RagQueryRequest.builder()
                    .requestId(requestId)
                    .userId(String.valueOf(workspace.getUser().getId()))
                    .workspaceId(workspace.getId())
                    .documentId(activeMappings.isEmpty() && selectedDocument != null ? selectedDocument.getId() : null)
                    // [] explicitly means system-KB-only; null preserves legacy single-document behavior.
                    .attachedDocumentIds(activeMappings.isEmpty() && selectedDocument != null
                            ? null
                            : attachedDocumentIds)
                    .chatSessionId(chatSession.getId())
                    .question(question)
                    .chatHistory(chatHistoryStr.isEmpty() ? null : chatHistoryStr)
                    .topKUserChunks(5)
                    .topKKnowledgeChunks(5)
                    .build();
            aiResponse = pythonAiClient.query(aiRequest);
        } catch (NoReadyDocumentsException ex) {
            userMessage.setStatus(ChatMessageStatus.FAILED);
            userMessage.setErrorMessage(ex.getMessage());
            chatMessageRepository.save(userMessage);
            throw ex;
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

        assistantBuilder
                .confidenceScore(aiResponse.getConfidenceScore())
                .shouldSuggestTicket(aiResponse.getShouldSuggestTicket())
                .suggestionType(aiResponse.getSuggestionType())
                .suggestionReason(aiResponse.getSuggestionReason())
                .missingInformation(aiResponse.getMissingInformation())
                .riskLevel(aiResponse.getRiskLevel())
                .legalDomain(aiResponse.getLegalDomain())
                .userActionHint(aiResponse.getUserActionHint());

        ChatMessage assistantMessage = assistantBuilder.build();
        chatMessageRepository.save(assistantMessage);
        saveCitations(aiResponse.getCitations(), assistantMessage);
        subscriptionQuotaService.recordAiChatUsage(
                currentUser,
                assistantMessage.getPromptTokens() == null ? estimateTokens(question) : assistantMessage.getPromptTokens(),
                assistantMessage.getCompletionTokens() == null ? estimateTokens(aiResponse.getAnswer()) : assistantMessage.getCompletionTokens());

        // Update ChatSession timestamps
        chatSession.setLastMessageAt(LocalDateTime.now());
        chatSessionRepository.save(chatSession);

        return SendMessageResponse.builder()
                .chatSession(toChatSessionResponse(chatSession))
                .userMessage(toChatMessageResponse(userMessage))
                .assistantMessage(toChatMessageResponse(assistantMessage))
                .build();
    }

    private void updateSessionTitleIfNeeded(ChatSession chatSession, String question) {
        if (chatSession.getIsDefault() && "Default Conversation".equals(chatSession.getTitle())) {
            String title = question;
            if (title.length() > 100) {
                title = title.substring(0, 100);
            }
            chatSession.setTitle(title);
            chatSessionRepository.save(chatSession);
        }
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

    private void saveCitations(List<RagQueryResponse.Citation> citations, ChatMessage assistantMessage) {
        if (citations == null || citations.isEmpty()) {
            return;
        }

        List<AiCitation> entities = citations.stream()
                .map(citation -> {
                    boolean knowledgeBase = "SYSTEM_KB".equalsIgnoreCase(citation.getSourceType());
                    String sourceReferenceId = firstNonBlank(
                            knowledgeBase ? citation.getKnowledgeDocumentId() : citation.getDocumentId(),
                            citation.getDocumentId(),
                            citation.getCitationId());
                    String label = firstNonBlank(
                            citation.getLawName(),
                            citation.getFileName(),
                            citation.getSectionTitle(),
                            citation.getCitationId());
                    return AiCitation.builder()
                            .id("cite_" + UUID.randomUUID().toString().replace("-", ""))
                            .sourceType(knowledgeBase ? CitationSourceType.KNOWLEDGE_BASE : CitationSourceType.DOCUMENT)
                            .sourceReferenceId(sourceReferenceId)
                            .label(label)
                            .excerpt(citation.getExcerpt())
                            .pageNumber(citation.getPageNumber())
                            .score(citation.getScore())
                            .chatMessage(assistantMessage)
                            .build();
                })
                .toList();
        aiCitationRepository.saveAll(entities);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }
}
