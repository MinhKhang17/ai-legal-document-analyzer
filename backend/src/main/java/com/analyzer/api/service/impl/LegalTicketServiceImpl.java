package com.analyzer.api.service.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.LegalTicketMessageType;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.*;
import com.analyzer.api.service.LegalTicketService;
import com.analyzer.api.service.SubscriptionQuotaService;
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

    @Override
    @Transactional
    public LegalTicketResponse createTicket(Long customerId, CreateLegalTicketRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));
        subscriptionQuotaService.checkCanCreateExpertTicket(customer);

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
        if (requestId != null && !requestId.isBlank()) {
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

        String question = firstNonBlank(request.getQuestion(), request.getCustomerNote());
        if (question == null || question.isBlank()) {
            throw new ConflictException("QUESTION_REQUIRED");
        }

        String customerNote = firstNonBlank(request.getCustomerNote(), request.getQuestion());

        LegalTicket ticket = LegalTicket.builder()
                .requestId(requestId)
                .issueFingerprint(request.getIssueFingerprint())
                .customerNote(customerNote)
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

        LegalTicketMessage customerMsg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(customer)
                .content(question)
                .messageType(LegalTicketMessageType.CUSTOMER_REPLY)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(customerMsg);

        LegalTicketMessage systemMsg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(customer)
                .content("Ticket da duoc khoi tao thanh cong va dang cho Admin duyet.")
                .messageType(LegalTicketMessageType.SYSTEM)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(systemMsg);
        subscriptionQuotaService.recordExpertTicketUsage(customer);

        return legalTicketMapper.toResponse(ticket);
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

        return legalTicketMapper.toResponse(ticket);
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
                .map(legalTicketMapper::toResponse)
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

        return legalTicketMapper.toResponse(ticket);
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

        return legalTicketMapper.toResponse(ticket);
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

        return legalTicketMapper.toResponse(ticket);
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

        return legalTicketMapper.toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LegalTicketResponse> listAdminTickets(LegalTicketStatus status, RiskLevel riskLevel, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LegalTicket> pageResult;

        if (status != null && riskLevel != null) {
            pageResult = legalTicketRepository.findByStatusAndRiskLevelAndDeletedFalse(status, riskLevel, pageable);
        } else if (status != null) {
            pageResult = legalTicketRepository.findByStatusAndDeletedFalse(status, pageable);
        } else if (riskLevel != null) {
            pageResult = legalTicketRepository.findByRiskLevelAndDeletedFalse(riskLevel, pageable);
        } else {
            pageResult = legalTicketRepository.findAllByDeletedFalse(pageable);
        }

        List<LegalTicketResponse> list = pageResult.getContent().stream()
                .map(legalTicketMapper::toResponse)
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

        return legalTicketMapper.toResponse(ticket);
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
}
