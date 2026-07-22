package com.analyzer.api.service.impl;

import com.analyzer.api.client.PythonAiClient;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.ai.RagQueryRequest;
import com.analyzer.api.dto.ai.RagQueryResponse;
import com.analyzer.api.dto.ai.QueryContextSnapshot;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackRequest;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageResponse;
import com.analyzer.api.dto.chatmessage.SendMessageRequest;
import com.analyzer.api.dto.chatmessage.SendMessageResponse;
import com.analyzer.api.dto.chatsession.ChatSessionResponse;
import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.entity.ChatMessageFeedback;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.entity.ChatSessionDocument;
import com.analyzer.api.entity.AiCitation;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import com.analyzer.api.enums.ChatMessageType;
import com.analyzer.api.enums.ChatSessionStatus;
import com.analyzer.api.enums.ChatMode;
import com.analyzer.api.enums.CitationSourceType;
import com.analyzer.api.enums.AiFeedbackType;
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
import com.analyzer.api.service.PolicyAcceptanceService;
import com.analyzer.api.service.conversation.ConversationHistoryAssembler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageServiceImpl.class);

    private static final int SYSTEM_PROMPT_TOKEN_OVERHEAD = 2_500;

    private final WorkspaceRepository workspaceRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PythonAiClient pythonAiClient;
    private final UserRepository userRepository;
    private final SubscriptionQuotaService subscriptionQuotaService;
    private final PolicyAcceptanceService policyAcceptanceService;
    private final AiCitationRepository aiCitationRepository;
    private final ChatMessageFeedbackRepository chatMessageFeedbackRepository;
    private final ChatSessionDocumentRepository chatSessionDocumentRepository;
    private final ConversationHistoryAssembler conversationHistoryAssembler;
    // Use Spring Boot's configured mapper so Java time values in the immutable
    // query snapshot are serialized with the registered JavaTimeModule.
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(noRollbackFor = { AiServiceUnavailableException.class, AiServiceTimeoutException.class,
            InvalidAiResponseException.class })
    public SendMessageResponse sendMessageInWorkspace(Long userId, String workspaceId, SendMessageRequest request) {
        // Validate request
        validateMessageRequest(request);
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
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

        ensureAttachedDocumentsReady(chatSession, userId);
        requirePoliciesForDocumentProcessing(chatSession, userId, selectedDocument);

        String requestId = resolveRequestId(request);
        Optional<SendMessageResponse> replay = replayCompletedRequest(currentUser, chatSession, requestId);
        if (replay.isPresent()) {
            return replay.get();
        }
        subscriptionQuotaService.reserveAiChatQuota(currentUser, requestId,
                estimateTokens(request.getMessage()) + SYSTEM_PROMPT_TOKEN_OVERHEAD);

        // Create User Message
        ChatMessage userMessage = createOrReuseUserMessage(chatSession, request.getMessage().trim(), requestId);

        // Process AI Query
        return executeAiQuery(currentUser, workspace, chatSession, userMessage, request.getMessage().trim(),
                selectedDocument, request, requestId);
    }

    @Override
    @Transactional(noRollbackFor = { AiServiceUnavailableException.class, AiServiceTimeoutException.class,
            InvalidAiResponseException.class })
    public SendMessageResponse sendMessageInChatSession(Long userId, String chatSessionId, SendMessageRequest request) {
        // Validate request
        validateMessageRequest(request);
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
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

        ensureAttachedDocumentsReady(chatSession, userId);
        requirePoliciesForDocumentProcessing(chatSession, userId, selectedDocument);

        String requestId = resolveRequestId(request);
        Optional<SendMessageResponse> replay = replayCompletedRequest(currentUser, chatSession, requestId);
        if (replay.isPresent()) {
            return replay.get();
        }
        subscriptionQuotaService.reserveAiChatQuota(currentUser, requestId,
                estimateTokens(request.getMessage()) + SYSTEM_PROMPT_TOKEN_OVERHEAD);

        // Create User Message
        ChatMessage userMessage = createOrReuseUserMessage(chatSession, request.getMessage().trim(), requestId);

        // Process AI Query
        return executeAiQuery(currentUser, workspace, chatSession, userMessage, request.getMessage().trim(),
                selectedDocument, request, requestId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> getMessagesByChatSession(Long userId, String chatSessionId, int page,
            int size) {
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
                Sort.by(Sort.Direction.ASC, "createdAt"));

        Page<ChatMessage> messagePage = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(chatSessionId,
                pageable);

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
            throw new DeletedChatSessionException("Message belongs to a deleted chat session", chatSession.getId(),
                    "DELETED");
        }

        return toChatMessageResponse(chatMessage);
    }

    @Override
    @Transactional
    public ChatMessageFeedbackResponse submitFeedback(Long userId, String messageId,
            ChatMessageFeedbackRequest request) {
        ChatMessage chatMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        if (!chatMessage.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to rate this message");
        }

        if (chatMessage.getRole() != ChatMessageRole.ASSISTANT) {
            throw new ForbiddenException("Only AI assistant messages can be rated");
        }

        AiFeedbackType feedbackType = resolveFeedbackType(request);
        FeedbackRating legacyRating = feedbackType == AiFeedbackType.LIKE
                ? FeedbackRating.THUMBS_UP
                : FeedbackRating.THUMBS_DOWN;
        ChatMessageFeedback feedback = chatMessageFeedbackRepository.findByChatMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> ChatMessageFeedback.builder()
                        .chatMessage(chatMessage)
                        .chatSession(chatMessage.getChatSession())
                        .user(chatMessage.getUser())
                        .build());
        feedback.setFeedbackType(feedbackType);
        feedback.setRating(legacyRating);
        feedback.setReasons(legacyRating == FeedbackRating.THUMBS_DOWN
                ? joinReasons(request.getReasons())
                : null);
        feedback.setReason(request.getReason());
        feedback.setComment(request.getComment());

        ChatMessageFeedback saved = chatMessageFeedbackRepository.save(feedback);
        return toChatMessageFeedbackResponse(saved);
    }

    @Override
    @Transactional
    public void removeFeedback(Long userId, String messageId) {
        ChatMessage chatMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (chatMessage.getRole() != ChatMessageRole.ASSISTANT || !chatMessage.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to remove feedback from this message");
        }
        chatMessageFeedbackRepository.findByChatMessageIdAndUserId(messageId, userId)
                .ifPresent(chatMessageFeedbackRepository::delete);
    }

    private static AiFeedbackType resolveFeedbackType(ChatMessageFeedbackRequest request) {
        if (request.getFeedbackType() != null)
            return request.getFeedbackType();
        if (request.getRating() == FeedbackRating.THUMBS_UP)
            return AiFeedbackType.LIKE;
        if (request.getRating() == FeedbackRating.THUMBS_DOWN)
            return AiFeedbackType.DISLIKE;
        throw new InvalidMessageException("feedbackType is required", true, 0);
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
        User submittedBy = feedback.getUser() != null ? feedback.getUser() : feedback.getChatMessage().getUser();
        return ChatMessageFeedbackResponse.builder()
                .id(feedback.getId())
                .messageId(feedback.getChatMessage().getId())
                .chatMessageId(feedback.getChatMessage().getId())
                .chatSessionId(feedback.getChatMessage().getChatSession().getId())
                .feedbackType(feedback.getFeedbackType())
                .reason(feedback.getReason())
                .messageContent(content != null && content.length() > 200 ? content.substring(0, 200) + "..." : content)
                .rating(feedback.getRating())
                .reasons(splitReasons(feedback.getReasons()))
                .comment(feedback.getComment())
                .submittedById(submittedBy.getId())
                .submittedByName(submittedBy.getFirstName() + " " + submittedBy.getLastName())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
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

    private void ensureAttachedDocumentsReady(ChatSession chatSession, Long userId) {
        List<ChatSessionDocument> activeMappings = chatSessionDocumentRepository
                .findByChatSessionIdAndUserIdAndActiveTrueOrderByAttachedAtAsc(chatSession.getId(), userId);
        ensureAttachedDocumentsReady(activeMappings, chatSession.getWorkspace().getId());
    }

    static void ensureAttachedDocumentsReady(List<ChatSessionDocument> activeMappings, String workspaceId) {
        if (activeMappings.isEmpty()) {
            return;
        }

        long readyCount = activeMappings.stream()
                .map(ChatSessionDocument::getDocument)
                .filter(document -> "READY".equalsIgnoreCase(document.getStatus()))
                .count();
        if (readyCount == activeMappings.size()) {
            return;
        }

        boolean hasFailedDocument = activeMappings.stream()
                .map(ChatSessionDocument::getDocument)
                .anyMatch(document -> "FAILED".equalsIgnoreCase(document.getStatus()));
        throw new NoReadyDocumentsException(
                hasFailedDocument
                        ? "An attached document failed processing. Remove it or upload it again before chatting"
                        : "Attached documents are still processing. Please wait until all attached documents are ready",
                workspaceId,
                readyCount,
                activeMappings.size() - readyCount);
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

    private ChatMessage createOrReuseUserMessage(ChatSession chatSession, String content, String requestId) {
        Optional<ChatMessage> existing = chatMessageRepository
                .findTopByRequestIdAndUserIdAndRoleOrderByCreatedAtDesc(
                        requestId, chatSession.getUser().getId(), ChatMessageRole.USER);
        if (existing.isPresent()) {
            ChatMessage message = existing.get();
            if (!message.getChatSession().getId().equals(chatSession.getId())
                    || !message.getContent().equals(content)) {
                throw new ConflictException("REQUEST_ID_REUSED",
                        "The requestId is already associated with different query content");
            }
            message.setStatus(ChatMessageStatus.PROCESSING);
            message.setErrorMessage(null);
            return chatMessageRepository.save(message);
        }
        String userMessageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        ChatMessage userMessage = ChatMessage.builder()
                .id(userMessageId)
                .chatSession(chatSession)
                .user(chatSession.getUser())
                .role(ChatMessageRole.USER)
                .messageType(ChatMessageType.NORMAL_CHAT)
                .content(content)
                .status(ChatMessageStatus.PROCESSING)
                .requestId(requestId)
                .build();
        return chatMessageRepository.save(userMessage);
    }

    private SendMessageResponse executeAiQuery(
            User currentUser,
            Workspace workspace,
            ChatSession chatSession,
            ChatMessage userMessage,
            String question,
            Document selectedDocument,
            SendMessageRequest sendRequest,
            String requestId) {
        userMessage.setRequestId(requestId);

        if (chatSession.getIsDefault() && "Default Conversation".equals(chatSession.getTitle())) {
            String title = question;
            if (title.length() > 100) {
                title = title.substring(0, 100);
            }
            chatSession.setTitle(title);
        }

        RagQueryResponse aiResponse;
        ChatMessage assistantMessage = chatMessageRepository
                .findTopByRequestIdAndUserIdAndRoleOrderByCreatedAtDesc(
                        requestId, currentUser.getId(), ChatMessageRole.ASSISTANT)
                .orElseGet(() -> ChatMessage.builder()
                        .id("msg_" + UUID.randomUUID().toString().replace("-", ""))
                        .chatSession(chatSession)
                        .user(chatSession.getUser())
                        .role(ChatMessageRole.ASSISTANT)
                        .messageType(ChatMessageType.NORMAL_CHAT)
                        .content("")
                        .requestId(requestId)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
        String assistantMessageId = assistantMessage.getId();
        ChatMode resolvedMode = ChatMode.LEGAL_QA;
        try {
            var activeMappings = chatSessionDocumentRepository
                    .findByChatSessionIdAndUserIdAndActiveTrueOrderByAttachedAtAsc(chatSession.getId(),
                            currentUser.getId());
            ensureAttachedDocumentsReady(activeMappings, workspace.getId());
            List<String> attachedDocumentIds = activeMappings.stream()
                    .map(mapping -> mapping.getDocument())
                    .map(Document::getId)
                    .distinct()
                    .toList();
            List<String> requestedDocumentIds = sendRequest.getDocumentIds() != null
                    && !sendRequest.getDocumentIds().isEmpty()
                            ? sendRequest.getDocumentIds()
                            : sendRequest.getMessageAttachedDocumentIds();
            List<String> messageAttachedDocumentIds = resolveMessageAttachedDocumentIds(
                    currentUser.getId(), workspace.getId(), requestedDocumentIds);
            String focusedDocumentId = resolveFocusedDocumentId(
                    currentUser.getId(), workspace.getId(), sendRequest.getFocusedDocumentId(), selectedDocument);
            resolvedMode = resolveChatMode(attachedDocumentIds, messageAttachedDocumentIds, selectedDocument);
            updateSessionConversationState(
                    chatSession,
                    attachedDocumentIds,
                    focusedDocumentId,
                    messageAttachedDocumentIds,
                    sendRequest.getUserRole(),
                    resolvedMode.name());
            ConversationHistoryAssembler.MemoryWindow memoryWindow = conversationHistoryAssembler.build(
                    chatSession, attachedDocumentIds);
            RagQueryRequest aiRequest = RagQueryRequest.builder()
                    .requestId(requestId)
                    .userId(String.valueOf(workspace.getUser().getId()))
                    .workspaceId(workspace.getId())
                    .documentId(activeMappings.isEmpty() && selectedDocument != null ? selectedDocument.getId() : null)
                    .attachedDocumentIds(activeMappings.isEmpty() && selectedDocument != null
                            ? null
                            : attachedDocumentIds)
                    .chatSessionId(chatSession.getId())
                    .question(question)
                    .chatHistory(null)
                    .conversationSummaryJson(chatSession.getConversationSummaryJson())
                    .recentHistory(memoryWindow.recentHistory())
                    .evictedMessages(memoryWindow.evictedMessages())
                    .currentUserMessageId(userMessage.getId())
                    .currentAssistantMessageId(assistantMessageId)
                    .focusedDocumentId(focusedDocumentId)
                    .messageAttachedDocumentIds(messageAttachedDocumentIds)
                    .conversationUserRole(chatSession.getConversationUserRole())
                    .conversationMode(chatSession.getConversationMode())
                    .draftingAction(sendRequest.getDraftingAction())
                    .draftingContractType(sendRequest.getDraftingContractType())
                    .draftingInformation(sendRequest.getDraftingInformation())
                    .draftingOriginalRequirement(sendRequest.getDraftingOriginalRequirement())
                    .topKUserChunks(5)
                    .topKKnowledgeChunks(5)
                    .build();
            QueryContextSnapshot contextSnapshot = QueryContextSnapshot.builder()
                    .requestId(requestId)
                    .userId(currentUser.getId())
                    .workspaceId(workspace.getId())
                    .chatSessionId(chatSession.getId())
                    .attachedDocumentIds(List.copyOf(attachedDocumentIds))
                    .documentVersions(activeMappings.stream()
                            .map(mapping -> mapping.getDocument())
                            .map(document -> new QueryContextSnapshot.DocumentVersion(
                                    document.getId(), document.getUpdatedAt(), document.getStatus()))
                            .toList())
                    .messageAttachedDocumentIds(List.copyOf(messageAttachedDocumentIds))
                    .focusedDocumentId(focusedDocumentId)
                    .conversationSummary(chatSession.getConversationSummaryJson())
                    .recentHistory(List.copyOf(memoryWindow.recentHistory()))
                    .planType(subscriptionQuotaService.getCurrentPlan(currentUser).getPlanType())
                    .termsVersion(policyAcceptanceService.currentTermsVersion())
                    .privacyPolicyVersion(policyAcceptanceService.currentPrivacyVersion())
                    .estimatedTokens(estimateTokens(question) + SYSTEM_PROMPT_TOKEN_OVERHEAD)
                    .createdAt(LocalDateTime.now())
                    .build();
            String contextSnapshotJson = objectMapper.writeValueAsString(contextSnapshot);
            assistantMessage.setStatus(ChatMessageStatus.PROCESSING);
            assistantMessage.setContent("");
            assistantMessage.setErrorMessage(null);
            assistantMessage.setResolvedMode(resolvedMode);
            assistantMessage.setContextSnapshotJson(contextSnapshotJson);
            assistantMessage = chatMessageRepository.save(assistantMessage);
            subscriptionQuotaService.attachAiQueryContext(
                    currentUser, requestId, workspace.getId(), chatSession.getId(), contextSnapshotJson);
            aiResponse = pythonAiClient.query(aiRequest);
            java.util.Set<String> allowedDocumentIds = new java.util.HashSet<>(attachedDocumentIds);
            allowedDocumentIds.addAll(messageAttachedDocumentIds);
            if (selectedDocument != null) allowedDocumentIds.add(selectedDocument.getId());
            validateAiResponse(aiResponse, requestId, allowedDocumentIds);
        } catch (NoReadyDocumentsException ex) {
            userMessage.setStatus(ChatMessageStatus.FAILED);
            userMessage.setErrorMessage(ex.getMessage());
            chatMessageRepository.save(userMessage);
            subscriptionQuotaService.failAiChatQuota(currentUser, requestId, "DOCUMENT_NOT_READY");
            throw ex;
        } catch (JacksonException ex) {
            subscriptionQuotaService.failAiChatQuota(currentUser, requestId, "INVALID_QUERY_SNAPSHOT");
            throw new InvalidAiResponseException("Unable to create a safe immutable query context");
        } catch (InvalidAiResponseException ex) {
            userMessage.setStatus(ChatMessageStatus.FAILED);
            userMessage.setErrorMessage(ex.getMessage());
            chatMessageRepository.save(userMessage);
            assistantMessage.setStatus(ChatMessageStatus.FAILED);
            assistantMessage.setErrorMessage(ex.getMessage());
            chatMessageRepository.save(assistantMessage);
            subscriptionQuotaService.failAiChatQuota(currentUser, requestId, ex.getErrorCode());
            throw ex;
        } catch (AiServiceUnavailableException | AiServiceTimeoutException ex) {
            // Persist failed user message state
            userMessage.setStatus(ChatMessageStatus.FAILED);
            userMessage.setErrorMessage(ex.getMessage());
            chatMessageRepository.save(userMessage);

            // Persist failed assistant message
            assistantMessage.setContent("AI service is currently unavailable. Please try again later.");
            assistantMessage.setStatus(ChatMessageStatus.FAILED);
            assistantMessage.setResolvedMode(resolvedMode);
            assistantMessage.setErrorMessage(ex.getMessage());
            chatMessageRepository.save(assistantMessage);
            subscriptionQuotaService.failAiChatQuota(currentUser, requestId,
                    ex instanceof AiServiceTimeoutException ? "AI_PROVIDER_TIMEOUT" : "AI_SERVICE_UNAVAILABLE");

            // Update session timestamp
            chatSession.setLastMessageAt(LocalDateTime.now());
            chatSessionRepository.save(chatSession);

            throw ex;
        } catch (RuntimeException ex) {
            userMessage.setStatus(ChatMessageStatus.FAILED);
            userMessage.setErrorMessage(ex.getMessage());
            chatMessageRepository.save(userMessage);
            assistantMessage.setStatus(ChatMessageStatus.FAILED);
            assistantMessage.setErrorMessage(ex.getMessage());
            chatMessageRepository.save(assistantMessage);
            subscriptionQuotaService.failAiChatQuota(currentUser, requestId, "QUERY_EXECUTION_FAILED");
            throw ex;
        }

        // AI succeeded - update user message
        userMessage.setStatus(ChatMessageStatus.COMPLETED);
        chatMessageRepository.save(userMessage);

        assistantMessage.setContent(aiResponse.getAnswer());
        assistantMessage.setStatus(ChatMessageStatus.COMPLETED);
        assistantMessage.setResolvedMode(resolvedMode);
        assistantMessage.setAiModel(aiResponse.getModel());

        if (aiResponse.getUsage() != null) {
            assistantMessage.setPromptTokens(aiResponse.getUsage().getPromptTokens());
            assistantMessage.setCompletionTokens(aiResponse.getUsage().getCompletionTokens());
            assistantMessage.setTotalTokens(aiResponse.getUsage().getTotalTokens());
        }

        assistantMessage.setConfidenceScore(aiResponse.getConfidenceScore());
        assistantMessage.setShouldSuggestTicket(aiResponse.getShouldSuggestTicket());
        assistantMessage.setSuggestionType(aiResponse.getSuggestionType());
        assistantMessage.setSuggestionReason(aiResponse.getSuggestionReason());
        assistantMessage.setMissingInformation(aiResponse.getMissingInformation());
        assistantMessage.setRiskLevel(aiResponse.getRiskLevel());
        assistantMessage.setLegalDomain(aiResponse.getLegalDomain());
        assistantMessage.setUserActionHint(aiResponse.getUserActionHint());
        if ("DRAFT_CONTRACT".equals(aiResponse.getIntent())) {
            try {
                assistantMessage.setDraftingResponseJson(objectMapper.writeValueAsString(aiResponse));
            } catch (JacksonException exception) {
                logger.warn("Unable to persist drafting response metadata requestId={}", requestId, exception);
            }
        }
        chatMessageRepository.save(assistantMessage);
        applyConversationMemoryUpdate(chatSession, aiResponse.getConversationMemoryUpdate());
        saveCitations(chatSession.getWorkspace().getId(), aiResponse.getCitations(), assistantMessage);
        subscriptionQuotaService.completeAiChatQuota(
                currentUser,
                requestId,
                assistantMessage.getPromptTokens() == null ? estimateTokens(question)
                        : assistantMessage.getPromptTokens(),
                assistantMessage.getCompletionTokens() == null ? estimateTokens(aiResponse.getAnswer())
                        : assistantMessage.getCompletionTokens());

        // Update ChatSession timestamps
        chatSession.setLastMessageAt(LocalDateTime.now());
        chatSessionRepository.save(chatSession);

        return SendMessageResponse.builder()
                .chatSession(toChatSessionResponse(chatSession))
                .userMessage(toChatMessageResponse(userMessage))
                .assistantMessage(toChatMessageResponse(assistantMessage))
                .answer(aiResponse.getAnswer())
                .resolvedMode(resolvedMode)
                .sources(aiResponse.getCitations() == null ? List.of() : aiResponse.getCitations())
                .riskLevel(aiResponse.getRiskLevel())
                .suggestionType(aiResponse.getSuggestionType())
                .userActionHints(aiResponse.getUserActionHint() == null
                        ? List.of()
                        : List.of(aiResponse.getUserActionHint()))
                .assistantMessageId(assistantMessage.getId())
                .intent(aiResponse.getIntent())
                .intents(aiResponse.getIntents())
                .suggestedActions(aiResponse.getSuggestedActions())
                .selectedDocumentIds(aiResponse.getSelectedDocumentIds())
                .draftingPrompt(aiResponse.getDraftingPrompt())
                .redactionRequired(aiResponse.getRedactionRequired())
                .contractType(aiResponse.getContractType())
                .draftingStatus(aiResponse.getDraftingStatus())
                .questions(aiResponse.getQuestions() == null ? List.of() : aiResponse.getQuestions())
                .providedInformation(aiResponse.getProvidedInformation() == null ? java.util.Map.of() : aiResponse.getProvidedInformation())
                .draftingMissingInformation(aiResponse.getDraftingMissingInformation() == null ? List.of() : aiResponse.getDraftingMissingInformation())
                .privacyWarning(aiResponse.getPrivacyWarning())
                .draftingOriginalRequirement(aiResponse.getDraftingOriginalRequirement())
                .build();
    }

    private String resolveRequestId(SendMessageRequest request) {
        String requestId = request.getRequestId();
        if (requestId == null || requestId.isBlank()) {
            return "req_" + UUID.randomUUID().toString().replace("-", "");
        }
        return requestId.trim();
    }

    private void requirePoliciesForDocumentProcessing(ChatSession session, Long userId, Document selectedDocument) {
        if (selectedDocument != null
                || chatSessionDocumentRepository.countByChatSessionIdAndUserIdAndActiveTrue(session.getId(), userId) > 0) {
            policyAcceptanceService.requireCurrent(userId);
        }
    }

    private Optional<SendMessageResponse> replayCompletedRequest(
            User user, ChatSession session, String requestId) {
        Optional<ChatMessage> existingAssistant = chatMessageRepository
                .findTopByRequestIdAndUserIdAndRoleOrderByCreatedAtDesc(
                        requestId, user.getId(), ChatMessageRole.ASSISTANT);
        if (existingAssistant.isEmpty()) {
            return Optional.empty();
        }
        ChatMessage assistant = existingAssistant.get();
        if (!assistant.getChatSession().getId().equals(session.getId())) {
            throw new ConflictException("REQUEST_ID_REUSED",
                    "The requestId is already associated with another chat session");
        }
        if (assistant.getStatus() == ChatMessageStatus.PROCESSING) {
            throw new ConflictException("QUERY_ALREADY_PROCESSING", "This query is already processing");
        }
        if (assistant.getStatus() != ChatMessageStatus.COMPLETED) {
            return Optional.empty();
        }
        ChatMessage userMessage = chatMessageRepository
                .findTopByRequestIdAndUserIdAndRoleOrderByCreatedAtDesc(
                        requestId, user.getId(), ChatMessageRole.USER)
                .orElse(null);
        List<RagQueryResponse.Citation> citations = aiCitationRepository.findByChatMessage_Id(assistant.getId())
                .stream()
                .map(this::toStoredCitation)
                .toList();
        return Optional.of(SendMessageResponse.builder()
                .chatSession(toChatSessionResponse(session))
                .userMessage(toChatMessageResponse(userMessage))
                .assistantMessage(toChatMessageResponse(assistant))
                .answer(assistant.getContent())
                .resolvedMode(assistant.getResolvedMode())
                .sources(citations)
                .riskLevel(assistant.getRiskLevel())
                .suggestionType(assistant.getSuggestionType())
                .userActionHints(assistant.getUserActionHint() == null
                        ? List.of() : List.of(assistant.getUserActionHint()))
                .assistantMessageId(assistant.getId())
                .build());
    }

    private RagQueryResponse.Citation toStoredCitation(AiCitation citation) {
        boolean knowledgeBase = citation.getSourceType() == CitationSourceType.KNOWLEDGE_BASE;
        return RagQueryResponse.Citation.builder()
                .citationId(citation.getSourceReferenceId())
                .sourceType(knowledgeBase ? "SYSTEM_KB" : "USER_DOCUMENT")
                .documentId(knowledgeBase ? null : citation.getSourceReferenceId())
                .knowledgeDocumentId(knowledgeBase ? citation.getSourceReferenceId() : null)
                .fileName(citation.getLabel())
                .sectionTitle(citation.getLabel())
                .excerpt(citation.getExcerpt())
                .pageNumber(citation.getPageNumber())
                .score(citation.getScore())
                .build();
    }

    static void validateAiResponse(RagQueryResponse response, String requestId) {
        validateAiResponse(response, requestId, null);
    }

    static void validateAiResponse(RagQueryResponse response, String requestId,
                                   java.util.Set<String> allowedDocumentIds) {
        if (response == null || response.getAnswer() == null || response.getAnswer().isBlank()) {
            throw new InvalidAiResponseException("AI returned an empty or malformed response");
        }
        if (response.getRequestId() != null && !requestId.equals(response.getRequestId())) {
            throw new InvalidAiResponseException("AI response requestId does not match the query");
        }
        if (response.getConfidenceScore() != null
                && (response.getConfidenceScore() < 0 || response.getConfidenceScore() > 1)) {
            throw new InvalidAiResponseException("AI confidence must be between 0 and 1");
        }
        if (response.getLegalDomain() != null
                && response.getConfidenceScore() != null && response.getConfidenceScore() >= 0.8
                && (response.getCitations() == null || response.getCitations().isEmpty())) {
            throw new InvalidAiResponseException("High-confidence AI responses require supporting citations");
        }
        if (allowedDocumentIds != null && response.getCitations() != null) {
            boolean invalidUserCitation = response.getCitations().stream()
                    .filter(citation -> "USER_DOCUMENT".equalsIgnoreCase(citation.getSourceType()))
                    .map(RagQueryResponse.Citation::getDocumentId)
                    .anyMatch(documentId -> documentId == null || !allowedDocumentIds.contains(documentId));
            if (invalidUserCitation) {
                throw new InvalidAiResponseException("AI returned a citation outside the immutable document snapshot");
            }
        }
    }

    static ChatMode resolveChatMode(
            List<String> sessionDocumentIds,
            List<String> messageDocumentIds,
            Document legacySelectedDocument) {
        boolean hasSessionDocuments = sessionDocumentIds != null && !sessionDocumentIds.isEmpty();
        boolean hasMessageDocuments = messageDocumentIds != null && !messageDocumentIds.isEmpty();
        return hasSessionDocuments || hasMessageDocuments || legacySelectedDocument != null
                ? ChatMode.DOCUMENT_ANALYSIS
                : ChatMode.LEGAL_QA;
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
        RagQueryResponse drafting = readPersistedDraftingResponse(message.getDraftingResponseJson());
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
                .resolvedMode(message.getResolvedMode())
                .promptTokens(message.getPromptTokens())
                .completionTokens(message.getCompletionTokens())
                .totalTokens(message.getTotalTokens())
                .errorMessage(message.getErrorMessage())
                .intent(drafting == null ? null : drafting.getIntent())
                .suggestedActions(drafting == null ? List.of() : drafting.getSuggestedActions())
                .draftingPrompt(drafting == null ? null : drafting.getDraftingPrompt())
                .redactionRequired(drafting == null ? null : drafting.getRedactionRequired())
                .contractType(drafting == null ? null : drafting.getContractType())
                .draftingStatus(drafting == null ? null : drafting.getDraftingStatus())
                .questions(drafting == null || drafting.getQuestions() == null ? List.of() : drafting.getQuestions())
                .providedInformation(drafting == null || drafting.getProvidedInformation() == null ? java.util.Map.of() : drafting.getProvidedInformation())
                .draftingMissingInformation(drafting == null || drafting.getDraftingMissingInformation() == null ? List.of() : drafting.getDraftingMissingInformation())
                .privacyWarning(drafting == null ? null : drafting.getPrivacyWarning())
                .draftingOriginalRequirement(drafting == null ? null : drafting.getDraftingOriginalRequirement())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }

    private RagQueryResponse readPersistedDraftingResponse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, RagQueryResponse.class);
        } catch (JacksonException exception) {
            logger.warn("Unable to read persisted drafting response metadata", exception);
            return null;
        }
    }

    private void saveCitations(String workspaceId, List<RagQueryResponse.Citation> citations,
            ChatMessage assistantMessage) {
        if (citations == null || citations.isEmpty()) {
            return;
        }

        List<AiCitation> entities = citations.stream()
                .filter(citation -> citation.getCitationId() != null && !citation.getCitationId().isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        citation -> citation.getCitationId().toUpperCase(),
                        citation -> citation,
                        (first, duplicate) -> first,
                        java.util.LinkedHashMap::new))
                .values().stream()
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

                    String uri = null;
                    if (knowledgeBase) {
                        if (citation.getFileName() != null && !citation.getFileName().isBlank()) {
                            try {
                                uri = "/api/v1/workspaces/" + workspaceId + "/documents/system/download?filename="
                                        + java.net.URLEncoder.encode(citation.getFileName(),
                                                java.nio.charset.StandardCharsets.UTF_8.toString());
                            } catch (java.io.UnsupportedEncodingException e) {
                                uri = "/api/v1/workspaces/" + workspaceId + "/documents/system/download?filename="
                                        + citation.getFileName();
                            }
                        }
                    } else {
                        if (citation.getDocumentId() != null && !citation.getDocumentId().isBlank()) {
                            uri = "/api/v1/workspaces/" + workspaceId + "/documents/" + citation.getDocumentId()
                                    + "/download";
                        }
                    }

                    return AiCitation.builder()
                            .id("cite_" + UUID.randomUUID().toString().replace("-", ""))
                            .sourceType(knowledgeBase ? CitationSourceType.KNOWLEDGE_BASE : CitationSourceType.DOCUMENT)
                            .sourceReferenceId(sourceReferenceId)
                            .label(label)
                            .excerpt(citation.getExcerpt())
                            .pageNumber(citation.getPageNumber())
                            .score(citation.getScore())
                            .chatMessage(assistantMessage)
                            .uri(uri)
                            .build();
                })
                .toList();
        aiCitationRepository.saveAll(entities);
    }

    private List<String> resolveMessageAttachedDocumentIds(
            Long userId, String workspaceId, List<String> requestedDocumentIds) {
        if (requestedDocumentIds == null || requestedDocumentIds.isEmpty())
            return List.of();
        List<String> validated = new ArrayList<>();
        for (String documentId : requestedDocumentIds) {
            if (documentId == null || documentId.isBlank() || validated.contains(documentId.trim()))
                continue;
            validated.add(resolveSelectedDocument(userId, workspaceId, documentId).getId());
        }
        return List.copyOf(validated);
    }

    private String resolveFocusedDocumentId(
            Long userId, String workspaceId, String requestedFocusedDocumentId, Document selectedDocument) {
        if (requestedFocusedDocumentId != null && !requestedFocusedDocumentId.isBlank()) {
            return resolveSelectedDocument(userId, workspaceId, requestedFocusedDocumentId).getId();
        }
        return selectedDocument == null ? null : selectedDocument.getId();
    }

    private void updateSessionConversationState(
            ChatSession session,
            List<String> activeDocumentIds,
            String focusedDocumentId,
            List<String> messageAttachedDocumentIds,
            String userRole,
            String conversationMode) {
        try {
            session.setActiveDocumentIdsJson(objectMapper.writeValueAsString(activeDocumentIds));
            session.setMessageAttachedDocumentIdsJson(objectMapper.writeValueAsString(messageAttachedDocumentIds));
        } catch (JacksonException exception) {
            logger.warn("Unable to serialize conversation document state for session {}", session.getId());
        }
        session.setFocusedDocumentId(focusedDocumentId);
        if (userRole != null && !userRole.isBlank())
            session.setConversationUserRole(userRole.trim());
        if (conversationMode != null && !conversationMode.isBlank())
            session.setConversationMode(conversationMode.trim());
        session.setMemoryUpdatedAt(LocalDateTime.now());
    }

    private void applyConversationMemoryUpdate(
            ChatSession session, RagQueryResponse.ConversationMemoryUpdate memoryUpdate) {
        if (memoryUpdate == null || !Boolean.TRUE.equals(memoryUpdate.getUpdated())
                || memoryUpdate.getSummaryJson() == null || memoryUpdate.getSummaryJson().isBlank()) {
            return;
        }
        session.setConversationSummaryJson(memoryUpdate.getSummaryJson());
        session.setSummary(memoryUpdate.getSummaryJson());
        session.setMemoryJson(memoryUpdate.getSummaryJson());
        session.setSummaryThroughMessageId(memoryUpdate.getSummarizedThroughMessageId());
        session.setSummaryUpdatedAt(LocalDateTime.now());
        session.setMemoryUpdatedAt(LocalDateTime.now());
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
