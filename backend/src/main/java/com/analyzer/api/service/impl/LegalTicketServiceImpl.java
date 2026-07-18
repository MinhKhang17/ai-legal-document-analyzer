package com.analyzer.api.service.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.LegalTicketMessageType;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketType;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.*;
import com.analyzer.api.service.LegalTicketService;
import com.analyzer.api.service.EmailService;
import com.analyzer.api.service.SubscriptionQuotaService;
import com.analyzer.api.service.TicketCollaborationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LegalTicketServiceImpl implements LegalTicketService {

    private final LegalTicketRepository legalTicketRepository;
    private final LegalTicketMessageRepository legalTicketMessageRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final DocumentRepository documentRepository;
    private final LegalTicketMapper legalTicketMapper;
    private final SubscriptionQuotaService subscriptionQuotaService;
    private final EmailService emailService;
    private final TicketContextSnapshotRepository snapshotRepository;
    private final TicketCollaborationService collaborationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public LegalTicketResponse createTicket(Long customerId, CreateLegalTicketRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));
        LegalTicketType requestedType = request.getTicketType() != null ? request.getTicketType() : LegalTicketType.CONTACT_EXPERT;
        if (requestedType == LegalTicketType.CONTACT_EXPERT
                && request.getRecipientType() != com.analyzer.api.enums.TicketRecipientType.ADMIN) {
            subscriptionQuotaService.checkCanCreateExpertTicket(customer);
        }

        Workspace workspace = workspaceRepository.findById(request.getWorkspaceId())
                .orElseThrow(() -> new ForbiddenException("WORKSPACE_ACCESS_DENIED"));
        if (workspace.getUser() == null || !workspace.getUser().getId().equals(customerId)) {
            throw new ForbiddenException("WORKSPACE_ACCESS_DENIED");
        }

        Document document = null;
        if (request.getDocumentId() != null && !request.getDocumentId().isBlank()) {
            document = documentRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException("DOCUMENT_NOT_FOUND"));
            if (document.getWorkspace() == null || !document.getWorkspace().getId().equals(request.getWorkspaceId())) {
                throw new ForbiddenException("DOCUMENT_NOT_IN_WORKSPACE");
            }
        }

        String requestId = request.getRequestId();
        ChatMessage chatMsg = null;
        if (request.getAssistantMessageId() != null && !request.getAssistantMessageId().isBlank()) {
            chatMsg = requireOwnedMessage(request.getAssistantMessageId(), customerId, ChatMessageRole.ASSISTANT);
            requestId = chatMsg.getRequestId();
        } else if (requestId != null && !requestId.isBlank()) {
            chatMsg = chatMessageRepository
                    .findTopByRequestIdAndUserIdAndRoleOrderByCreatedAtDesc(
                            requestId,
                            customerId,
                            ChatMessageRole.ASSISTANT)
                    .orElseThrow(() -> new ResourceNotFoundException("AI_ANALYSIS_NOT_FOUND"));

            Optional<LegalTicket> existingTicket = legalTicketRepository
                    .findByRequestIdAndCreatedByIdAndDeletedFalse(requestId, customerId);
            if (existingTicket.isPresent()) {
                throw new ConflictException("DUPLICATE_TICKET");
            }
        } else {
            requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        }

        if (requestId == null || requestId.isBlank()) {
            requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        }
        if (legalTicketRepository
                .findByRequestIdAndCreatedByIdAndDeletedFalse(requestId, customerId).isPresent()) {
            throw new ConflictException("DUPLICATE_TICKET");
        }

        ChatMessage userMessage = null;
        if (request.getUserMessageId() != null && !request.getUserMessageId().isBlank()) {
            userMessage = requireOwnedMessage(request.getUserMessageId(), customerId, ChatMessageRole.USER);
        }
        if (chatMsg != null && userMessage != null
                && !chatMsg.getChatSession().getId().equals(userMessage.getChatSession().getId())) {
            throw new ConflictException("SOURCE_MESSAGES_NOT_IN_SAME_SESSION");
        }
        List<Document> sharedDocuments = resolveSharedDocuments(request, customerId, workspace);

        String question = firstNonBlank(userMessage != null ? userMessage.getContent() : null,
                request.getQuestion(), request.getCustomerNote());
        if (question == null || question.isBlank()) {
            throw new ConflictException("QUESTION_REQUIRED");
        }

        String customerNote = firstNonBlank(request.getCustomerNote(), request.getQuestion());

        LegalTicket ticket = LegalTicket.builder()
                .requestId(requestId)
                .issueFingerprint(request.getIssueFingerprint())
                .customerNote(customerNote)
                .ticketType(requestedType)
                .relatedChatSessionId(firstNonBlank(request.getChatSessionId(),
                        chatMsg != null && chatMsg.getChatSession() != null ? chatMsg.getChatSession().getId() : null))
                .relatedChatMessageId(firstNonBlank(request.getChatMessageId(), chatMsg != null ? chatMsg.getId() : null))
                .sourceUserMessageId(userMessage != null ? userMessage.getId() : request.getUserMessageId())
                .sourceAssistantMessageId(chatMsg != null ? chatMsg.getId() : request.getAssistantMessageId())
                .recipientType(request.getRecipientType())
                .title(firstNonBlank(request.getTitle(), question.length() > 120 ? question.substring(0, 120) : question))
                .description(firstNonBlank(request.getDescription(), customerNote))
                .priority(request.getPriority())
                .conversationScope(request.getConversationScope())
                .focusedDocumentId(request.getFocusedDocumentId())
                .sharedDocumentIdsJson(writeJson(sharedDocuments.stream().map(Document::getId).toList()))
                .createdBy(customer)
                .workspace(workspace)
                .document(document)
                .question(question)
                .answer(chatMsg != null ? chatMsg.getContent() : null)
                .confidenceScore(chatMsg != null ? chatMsg.getConfidenceScore() : null)
                .shouldSuggestTicket(chatMsg != null ? chatMsg.getShouldSuggestTicket() : null)
                .suggestionType(chatMsg != null ? chatMsg.getSuggestionType() : null)
                .suggestionReason(chatMsg != null ? chatMsg.getSuggestionReason() : null)
                .missingInformation(chatMsg != null ? chatMsg.getMissingInformation() : null)
                .riskLevel(chatMsg != null ? chatMsg.getRiskLevel() : null)
                .legalDomain(chatMsg != null ? chatMsg.getLegalDomain() : null)
                .userActionHint(chatMsg != null ? chatMsg.getUserActionHint() : null)
                .status(LegalTicketStatus.PENDING_ADMIN_REVIEW)
                .lastCustomerMessageAt(LocalDateTime.now())
                .build();

        ticket = legalTicketRepository.save(ticket);

        snapshotRepository.save(buildSnapshot(ticket, userMessage, chatMsg, sharedDocuments));
        collaborationService.claimForTicket(request.getAttachmentIds(), customerId, ticket.getId());
        collaborationService.auditTicket(ticket, customer, "TICKET_CREATED", "{\"sourceAssistantMessageId\":\""
                + Optional.ofNullable(ticket.getSourceAssistantMessageId()).orElse("") + "\"}");

        LegalTicketMessage customerMsg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(customer)
                .senderRole(RoleName.CUSTOMER.name())
                .content(question)
                .messageType(LegalTicketMessageType.CUSTOMER_REPLY)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(customerMsg);

        LegalTicketMessage systemMsg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(customer)
                .senderRole("SYSTEM")
                .content("Ticket da duoc khoi tao thanh cong va dang cho Admin duyet.")
                .messageType(LegalTicketMessageType.SYSTEM)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(systemMsg);
        if (requestedType == LegalTicketType.CONTACT_EXPERT
                && request.getRecipientType() != com.analyzer.api.enums.TicketRecipientType.ADMIN) {
            subscriptionQuotaService.recordExpertTicketUsage(customer);
        }

        for (User admin : userRepository.findAllByRole_NameAndActiveTrue(RoleName.ADMIN)) {
            emailService.sendTicketNotificationAsync(admin.getEmail(), admin.getFirstName(), ticket.getId(),
                    ticket.getTicketType().name(), ticket.getStatus().name(), "/admin/tickets/" + ticket.getId(),
                    "Co ticket moi tu " + customer.getEmail());
        }

        return toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public LegalTicketResponse getTicketById(Long userId, String userRole, String ticketId) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        if (RoleName.CUSTOMER.name().equalsIgnoreCase(userRole)) {
            if (!ticket.getCreatedBy().getId().equals(userId)) {
                throw new ForbiddenException("Ban khong co quyen truy cap ticket nay");
            }
        } else if (RoleName.EXPERT.name().equalsIgnoreCase(userRole)) {
            if (ticket.getAssignedLawyer() == null || !ticket.getAssignedLawyer().getId().equals(userId)) {
                throw new ForbiddenException("NOT_ASSIGNED_EXPERT");
            }
        }

        return toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LegalTicketResponse> getMyTickets(Long customerId, LegalTicketStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LegalTicket> pageResult;

        if (status != null) {
            pageResult = legalTicketRepository.findByCreatedByIdAndStatusAndDeletedFalse(customerId, status, pageable);
        } else {
            pageResult = legalTicketRepository.findByCreatedByIdAndDeletedFalse(customerId, pageable);
        }

        List<LegalTicketResponse> list = pageResult.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<LegalTicketResponse>builder()
                .items(list)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalItems(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public LegalTicketResponse cancelTicket(Long customerId, String ticketId, CancelLegalTicketRequest request) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        if (!ticket.getCreatedBy().getId().equals(customerId)) {
            throw new ForbiddenException("Ban khong co quyen huy ticket nay");
        }

        if (ticket.getStatus() != LegalTicketStatus.PENDING_ADMIN_REVIEW) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        ticket.setStatus(LegalTicketStatus.CANCELLED);
        ticket.setCancelledAt(LocalDateTime.now());

        String reason = request != null && request.getReason() != null ? request.getReason() : "Khong co ly do";

        LegalTicketMessage msg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(ticket.getCreatedBy())
                .content("Khach hang da huy ticket. Ly do: " + reason)
                .messageType(LegalTicketMessageType.SYSTEM)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(msg);

        return toResponse(ticket);
    }

    @Override
    @Transactional
    public LegalTicketResponse closeTicket(Long customerId, String ticketId, CloseLegalTicketRequest request) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        if (!ticket.getCreatedBy().getId().equals(customerId)) {
            throw new ForbiddenException("Ban khong co quyen dong ticket nay");
        }

        if (ticket.getStatus() != LegalTicketStatus.RESOLVED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        ticket.setStatus(LegalTicketStatus.CLOSED);
        ticket.setClosedAt(LocalDateTime.now());

        String feedback = request != null && request.getFeedback() != null ? request.getFeedback() : "";

        LegalTicketMessage msg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(ticket.getCreatedBy())
                .content("Khach hang da dong ticket. Phan hoi: " + feedback)
                .messageType(LegalTicketMessageType.SYSTEM)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(msg);

        return toResponse(ticket);
    }

    @Override
    @Transactional
    public LegalTicketResponse reopenTicket(Long customerId, String ticketId, ReopenLegalTicketRequest request) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        if (!ticket.getCreatedBy().getId().equals(customerId)) {
            throw new ForbiddenException("Ban khong co quyen mo lai ticket nay");
        }

        if (ticket.getStatus() != LegalTicketStatus.RESOLVED && ticket.getStatus() != LegalTicketStatus.CLOSED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        LocalDateTime referenceTime = ticket.getResolvedAt() != null ? ticket.getResolvedAt() : ticket.getClosedAt();
        if (referenceTime == null || referenceTime.plusDays(7).isBefore(LocalDateTime.now())) {
            throw new ConflictException("Chi cho phep mo lai ticket trong vong 7 ngay ke tu khi duoc giai quyet hoac dong");
        }

        ticket.setStatus(LegalTicketStatus.REOPENED);
        ticket.setReopenedAt(LocalDateTime.now());

        String reason = request != null ? request.getReason() : "Khong co ly do";

        LegalTicketMessage msg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(ticket.getCreatedBy())
                .content("Khach hang da mo lai ticket. Ly do: " + reason)
                .messageType(LegalTicketMessageType.CUSTOMER_REPLY)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(msg);

        return toResponse(ticket);
    }

    @Override
    @Transactional
    public LegalTicketResponse customerReply(Long customerId, String ticketId, CustomerTicketReplyRequest request) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        if (!ticket.getCreatedBy().getId().equals(customerId)) {
            throw new ForbiddenException("Ban khong co quyen phan hoi ticket nay");
        }

        if (ticket.getStatus() != LegalTicketStatus.ASSIGNED_TO_LAWYER &&
            ticket.getStatus() != LegalTicketStatus.IN_REVIEW &&
            ticket.getStatus() != LegalTicketStatus.NEED_MORE_INFO &&
            ticket.getStatus() != LegalTicketStatus.CUSTOMER_RESPONDED &&
            ticket.getStatus() != LegalTicketStatus.REOPENED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        ticket.setStatus(LegalTicketStatus.CUSTOMER_RESPONDED);
        ticket.setLastCustomerMessageAt(LocalDateTime.now());

        LegalTicketMessage msg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(ticket.getCreatedBy())
                .content(request.getMessage())
                .messageType(LegalTicketMessageType.CUSTOMER_REPLY)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(msg);

        return toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LegalTicketResponse> listAdminTickets(LegalTicketStatus status, RiskLevel riskLevel, LegalTicketType ticketType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LegalTicket> pageResult;

        if (status != null && riskLevel != null && ticketType != null) {
            pageResult = legalTicketRepository.findByStatusAndRiskLevelAndTicketTypeAndDeletedFalse(status, riskLevel, ticketType, pageable);
        } else if (status != null && ticketType != null) {
            pageResult = legalTicketRepository.findByStatusAndTicketTypeAndDeletedFalse(status, ticketType, pageable);
        } else if (riskLevel != null && ticketType != null) {
            pageResult = legalTicketRepository.findByRiskLevelAndTicketTypeAndDeletedFalse(riskLevel, ticketType, pageable);
        } else if (ticketType != null) {
            pageResult = legalTicketRepository.findByTicketTypeAndDeletedFalse(ticketType, pageable);
        } else if (status != null && riskLevel != null) {
            pageResult = legalTicketRepository.findByStatusAndRiskLevelAndDeletedFalse(status, riskLevel, pageable);
        } else if (status != null) {
            pageResult = legalTicketRepository.findByStatusAndDeletedFalse(status, pageable);
        } else if (riskLevel != null) {
            pageResult = legalTicketRepository.findByRiskLevelAndDeletedFalse(riskLevel, pageable);
        } else {
            pageResult = legalTicketRepository.findAllByDeletedFalse(pageable);
        }

        List<LegalTicketResponse> list = pageResult.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<LegalTicketResponse>builder()
                .items(list)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalItems(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public LegalTicketResponse rejectTicket(Long adminId, String ticketId, RejectLegalTicketRequest request) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        if (ticket.getStatus() != LegalTicketStatus.PENDING_ADMIN_REVIEW) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("ADMIN_NOT_FOUND"));

        ticket.setStatus(LegalTicketStatus.REJECTED_BY_ADMIN);
        ticket.setRejectionReason(request.getReason());

        LegalTicketMessage msg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(admin)
                .content("Admin tu choi yeu cau. Ly do: " + request.getReason())
                .messageType(LegalTicketMessageType.SYSTEM)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(msg);

        emailService.sendTicketNotificationAsync(ticket.getCreatedBy().getEmail(), ticket.getCreatedBy().getFirstName(),
                ticket.getId(), ticket.getTicketType() != null ? ticket.getTicketType().name() : "CONTACT_EXPERT",
                ticket.getStatus().name(), "/tickets/" + ticket.getId(), request.getReason());

        return toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegalTicketMessageResponse> getMessages(Long userId, String userRole, String ticketId) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        if (RoleName.CUSTOMER.name().equalsIgnoreCase(userRole)) {
            if (!ticket.getCreatedBy().getId().equals(userId)) {
                throw new ForbiddenException("Ban khong co quyen xem tin nhan cua ticket nay");
            }
        } else if (RoleName.EXPERT.name().equalsIgnoreCase(userRole)) {
            if (ticket.getAssignedLawyer() == null || !ticket.getAssignedLawyer().getId().equals(userId)) {
                throw new ForbiddenException("NOT_ASSIGNED_EXPERT");
            }
        }

        List<LegalTicketMessage> msgs = legalTicketMessageRepository.findByTicket_IdOrderByCreatedAtAsc(ticketId);

        return msgs.stream()
                .filter(m -> {
                    if (RoleName.CUSTOMER.name().equalsIgnoreCase(userRole)) {
                        return !Boolean.TRUE.equals(m.getInternalOnly());
                    }
                    return true;
                })
                .map(legalTicketMapper::toMessageResponse)
                .collect(Collectors.toList());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private ChatMessage requireOwnedMessage(String messageId, Long userId, ChatMessageRole role) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("SOURCE_MESSAGE_NOT_FOUND"));
        if (!message.getUser().getId().equals(userId) || message.getRole() != role) {
            throw new ForbiddenException("SOURCE_MESSAGE_ACCESS_DENIED");
        }
        return message;
    }

    private List<Document> resolveSharedDocuments(CreateLegalTicketRequest request, Long userId, Workspace workspace) {
        List<String> ids = new ArrayList<>(Optional.ofNullable(request.getDocumentIds()).orElse(List.of()));
        if (request.getDocumentId() != null && !request.getDocumentId().isBlank()) ids.add(request.getDocumentId());
        if (request.getFocusedDocumentId() != null && !request.getFocusedDocumentId().isBlank()) ids.add(request.getFocusedDocumentId());
        List<Document> documents = new ArrayList<>();
        for (String id : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            Document item = documentRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("DOCUMENT_NOT_FOUND"));
            if (!item.getWorkspace().getId().equals(workspace.getId()) || !item.getUser().getId().equals(userId)) {
                throw new ForbiddenException("DOCUMENT_ACCESS_DENIED");
            }
            documents.add(item);
        }
        return documents;
    }

    private TicketContextSnapshot buildSnapshot(LegalTicket ticket, ChatMessage userMessage,
                                                  ChatMessage assistantMessage, List<Document> documents) {
        List<Map<String, Object>> documentSnapshots = documents.stream().map(document -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("documentId", document.getId());
            item.put("fileName", document.getOriginalFileName());
            item.put("mimeType", document.getFileType());
            item.put("sizeBytes", document.getFileSize());
            item.put("status", document.getStatus());
            item.put("versionTimestamp", document.getUpdatedAt() != null ? document.getUpdatedAt().toString() : null);
            return item;
        }).toList();
        Map<String, Object> selectedMessages = new LinkedHashMap<>();
        selectedMessages.put("scope", Optional.ofNullable(ticket.getConversationScope())
                .orElse(com.analyzer.api.enums.ConversationScope.SELECTED_RESPONSE).name());
        selectedMessages.put("userMessageId", ticket.getSourceUserMessageId());
        selectedMessages.put("assistantMessageId", ticket.getSourceAssistantMessageId());
        selectedMessages.put("userQuestion", ticket.getQuestion());
        selectedMessages.put("assistantAnswer", ticket.getAnswer());
        var scope = Optional.ofNullable(ticket.getConversationScope())
                .orElse(com.analyzer.api.enums.ConversationScope.SELECTED_RESPONSE);
        if (scope != com.analyzer.api.enums.ConversationScope.TICKET_CONTEXT_ONLY
                && ticket.getRelatedChatSessionId() != null) {
            List<ChatMessage> sessionMessages = chatMessageRepository
                    .findByChatSessionIdOrderByCreatedAtAsc(ticket.getRelatedChatSessionId()).stream()
                    .filter(message -> message.getUser().getId().equals(ticket.getCreatedBy().getId()))
                    .toList();
            List<ChatMessage> scopedMessages;
            if (scope == com.analyzer.api.enums.ConversationScope.FULL_CONVERSATION) {
                scopedMessages = sessionMessages;
            } else if (scope == com.analyzer.api.enums.ConversationScope.RELATED_MESSAGES) {
                int selectedIndex = Math.max(0, sessionMessages.indexOf(userMessage));
                int fromIndex = Math.max(0, selectedIndex - 4);
                int toIndex = Math.min(sessionMessages.size(), selectedIndex + 4);
                scopedMessages = sessionMessages.subList(fromIndex, toIndex);
            } else {
                scopedMessages = sessionMessages.stream().filter(message ->
                        (userMessage != null && message.getId().equals(userMessage.getId()))
                                || (assistantMessage != null && message.getId().equals(assistantMessage.getId()))).toList();
            }
            selectedMessages.put("messages", scopedMessages.stream().map(message -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("messageId", message.getId());
                item.put("role", message.getRole().name());
                item.put("content", message.getContent());
                item.put("createdAt", message.getCreatedAt() != null ? message.getCreatedAt().toString() : null);
                return item;
            }).toList());
        }
        String citations = assistantMessage != null ? assistantMessage.getCitationMetadataJson() : null;
        String documentJson = writeJson(documentSnapshots);
        String selectedJson = writeJson(selectedMessages);
        String conversationTitle = assistantMessage != null && assistantMessage.getChatSession() != null
                ? assistantMessage.getChatSession().getTitle()
                : userMessage != null && userMessage.getChatSession() != null
                    ? userMessage.getChatSession().getTitle() : null;
        String hashInput = ticket.getQuestion() + "\n" + Optional.ofNullable(ticket.getAnswer()).orElse("")
                + "\n" + Optional.ofNullable(citations).orElse("") + "\n" + documentJson + "\n" + selectedJson;
        return TicketContextSnapshot.builder().ticket(ticket).userQuestion(ticket.getQuestion())
                .assistantAnswer(ticket.getAnswer()).conversationTitle(conversationTitle)
                .citationSnapshotJson(citations).documentSnapshotJson(documentJson)
                .selectedMessageSnapshotJson(selectedJson).contentHash(sha256(hashInput)).build();
    }

    private LegalTicketResponse toResponse(LegalTicket ticket) {
        LegalTicketResponse response = collaborationService.enrich(legalTicketMapper.toResponse(ticket), ticket);
        snapshotRepository.findByTicket_Id(ticket.getId()).ifPresent(snapshot -> response.setContextSnapshot(
                TicketContextSnapshotResponse.builder().id(snapshot.getId()).userQuestion(snapshot.getUserQuestion())
                        .assistantAnswer(snapshot.getAssistantAnswer()).conversationTitle(snapshot.getConversationTitle())
                        .citationSnapshotJson(snapshot.getCitationSnapshotJson())
                        .documentSnapshotJson(snapshot.getDocumentSnapshotJson())
                        .selectedMessageSnapshotJson(snapshot.getSelectedMessageSnapshotJson())
                        .contentHash(snapshot.getContentHash()).createdAt(snapshot.getCreatedAt()).build()));
        return response;
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new ConflictException("SNAPSHOT_SERIALIZATION_FAILED"); }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
