package com.analyzer.api.service.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.LegalTicketMessageType;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.*;
import com.analyzer.api.service.LegalTicketService;
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
    private final CustomerPlanRepository customerPlanRepository;
    private final LegalTicketMapper legalTicketMapper;

    @Override
    @Transactional
    public LegalTicketResponse createTicket(Long customerId, CreateLegalTicketRequest request) {
        // 1. Fetch original ChatMessage by requestId
        ChatMessage chatMsg = chatMessageRepository.findByRequestId(request.getRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("AI_ANALYSIS_NOT_FOUND"));

        // 2. Validate duplicate ticket
        Optional<LegalTicket> existingTicket = legalTicketRepository
                .findByRequestIdAndCreatedByIdAndDeletedFalse(request.getRequestId(), customerId);
        if (existingTicket.isPresent()) {
            throw new ConflictException("DUPLICATE_TICKET");
        }

        // 3. Validate user
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));

        // 4. Validate workspace ownership
        Workspace workspace = workspaceRepository.findById(request.getWorkspaceId())
                .orElseThrow(() -> new ForbiddenException("WORKSPACE_ACCESS_DENIED"));
        if (workspace.getUser() == null || !workspace.getUser().getId().equals(customerId)) {
            throw new ForbiddenException("WORKSPACE_ACCESS_DENIED");
        }

        // 5. Validate document in workspace (optional)
        Document document = null;
        if (request.getDocumentId() != null && !request.getDocumentId().isBlank()) {
            document = documentRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new RuntimeException("DOCUMENT_NOT_IN_WORKSPACE"));
            if (document.getWorkspace() == null || !document.getWorkspace().getId().equals(request.getWorkspaceId())) {
                throw new RuntimeException("DOCUMENT_NOT_IN_WORKSPACE");
            }
        }

        // 6. Subscription & Quota checks
        CustomerPlan activePlan = customerPlanRepository.findByCustomerIdAndStatus(customerId, PlanStatus.ACTIVE)
                .orElse(null);

        if (activePlan != null && activePlan.getEndDate() != null) {
            if (LocalDateTime.now().isAfter(activePlan.getEndDate())) {
                activePlan.setStatus(PlanStatus.EXPIRED);
                customerPlanRepository.save(activePlan);
                activePlan = null;
            }
        }

        if (activePlan == null) {
            throw new ForbiddenException("Bạn không có gói dịch vụ nào đang kích hoạt");
        }

        SubscriptionPlan subPlan = activePlan.getSubscriptionPlan();
        if (subPlan == null) {
            throw new ForbiddenException("EXPERT_REVIEW_NOT_INCLUDED");
        }

        String planName = subPlan.getPlanName() != null ? subPlan.getPlanName().toUpperCase() : "";
        if (planName.contains("FREE")) {
            throw new ForbiddenException("EXPERT_REVIEW_NOT_INCLUDED");
        }

        // TODO: SubscriptionPlan currently does not have dedicated expert review quota fields.
        // Once the schema supports expertReviewLimit, check if (activePlan.getUsedExpertReviews() >= subPlan.getMaxExpertReviews()).

        // 7. Create ticket using metadata from ChatMessage to avoid trusting client data
        LegalTicket ticket = LegalTicket.builder()
                .requestId(request.getRequestId())
                .issueFingerprint(request.getIssueFingerprint())
                .customerNote(request.getCustomerNote())
                .createdBy(customer)
                .workspace(workspace)
                .document(document)
                .question(chatMsg.getContent()) // From AI Chat log
                .answer(chatMsg.getContent()) // AI log answer snaps (or use chatMsg content as base)
                .confidenceScore(chatMsg.getConfidenceScore())
                .shouldSuggestTicket(chatMsg.getShouldSuggestTicket())
                .suggestionType(chatMsg.getSuggestionType())
                .suggestionReason(chatMsg.getSuggestionReason())
                .missingInformation(chatMsg.getMissingInformation())
                .riskLevel(chatMsg.getRiskLevel())
                .legalDomain(chatMsg.getLegalDomain())
                .userActionHint(chatMsg.getUserActionHint())
                .status(LegalTicketStatus.PENDING_ADMIN_REVIEW)
                .build();

        // Save Ticket (Hibernate triggers PrePersist)
        ticket = legalTicketRepository.save(ticket);

        // 8. Create SYSTEM message history
        LegalTicketMessage systemMsg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(customer) // Sender as creator, or could represent system
                .content("Ticket được khởi tạo thành công và đang chờ Admin duyệt.")
                .messageType(LegalTicketMessageType.SYSTEM)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(systemMsg);

        return legalTicketMapper.toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public LegalTicketResponse getTicketById(Long userId, String userRole, String ticketId) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        // Security check based on roles
        if (RoleName.CUSTOMER.name().equalsIgnoreCase(userRole)) {
            if (!ticket.getCreatedBy().getId().equals(userId)) {
                throw new ForbiddenException("Bạn không có quyền truy cập ticket này");
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
            throw new ForbiddenException("Bạn không có quyền hủy ticket này");
        }

        // Only allow cancel when status is PENDING_ADMIN_REVIEW or ASSIGNED_TO_LAWYER
        if (ticket.getStatus() != LegalTicketStatus.PENDING_ADMIN_REVIEW &&
            ticket.getStatus() != LegalTicketStatus.ASSIGNED_TO_LAWYER) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        ticket.setStatus(LegalTicketStatus.CANCELLED);
        ticket.setCancelledAt(LocalDateTime.now());

        String reason = request != null && request.getReason() != null ? request.getReason() : "Không có lý do";

        // Create System Message
        LegalTicketMessage msg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(ticket.getCreatedBy())
                .content("Khách hàng đã hủy ticket. Lý do: " + reason)
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
            throw new ForbiddenException("Bạn không có quyền đóng ticket này");
        }

        if (ticket.getStatus() != LegalTicketStatus.RESOLVED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        ticket.setStatus(LegalTicketStatus.CLOSED);
        ticket.setClosedAt(LocalDateTime.now());

        String feedback = request != null && request.getFeedback() != null ? request.getFeedback() : "";

        // Create System Message
        LegalTicketMessage msg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(ticket.getCreatedBy())
                .content("Khách hàng đã đóng ticket. Phản hồi: " + feedback)
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
            throw new ForbiddenException("Bạn không có quyền mở lại ticket này");
        }

        if (ticket.getStatus() != LegalTicketStatus.RESOLVED && ticket.getStatus() != LegalTicketStatus.CLOSED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        // Limit reopening to within 7 days
        LocalDateTime referenceTime = ticket.getResolvedAt() != null ? ticket.getResolvedAt() : ticket.getClosedAt();
        if (referenceTime == null || referenceTime.plusDays(7).isBefore(LocalDateTime.now())) {
            throw new ConflictException("Chỉ cho phép mở lại ticket trong vòng 7 ngày kể từ khi được giải quyết hoặc đóng");
        }

        ticket.setStatus(LegalTicketStatus.REOPENED);
        ticket.setReopenedAt(LocalDateTime.now());

        String reason = request != null ? request.getReason() : "Không có lý do";

        // Create System Message
        LegalTicketMessage msg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(ticket.getCreatedBy())
                .content("Khách hàng đã mở lại ticket. Lý do: " + reason)
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
            throw new ForbiddenException("Bạn không có quyền phản hồi ticket này");
        }

        if (ticket.getStatus() != LegalTicketStatus.NEED_MORE_INFO && ticket.getStatus() != LegalTicketStatus.REOPENED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        // Transition status to CUSTOMER_RESPONDED
        ticket.setStatus(LegalTicketStatus.CUSTOMER_RESPONDED);

        // Add Customer Reply message
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

        // Create System Message
        LegalTicketMessage msg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(admin)
                .content("Admin từ chối yêu cầu. Lý do: " + request.getReason())
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

        // Authorize access
        if (RoleName.CUSTOMER.name().equalsIgnoreCase(userRole)) {
            if (!ticket.getCreatedBy().getId().equals(userId)) {
                throw new ForbiddenException("Bạn không có quyền xem tin nhắn của ticket này");
            }
        } else if (RoleName.EXPERT.name().equalsIgnoreCase(userRole)) {
            if (ticket.getAssignedLawyer() == null || !ticket.getAssignedLawyer().getId().equals(userId)) {
                throw new ForbiddenException("NOT_ASSIGNED_EXPERT");
            }
        }

        List<LegalTicketMessage> msgs = legalTicketMessageRepository.findByTicket_IdOrderByCreatedAtAsc(ticketId);

        return msgs.stream()
                .filter(m -> {
                    // Customers cannot see internalOnly messages
                    if (RoleName.CUSTOMER.name().equalsIgnoreCase(userRole)) {
                        return !Boolean.TRUE.equals(m.getInternalOnly());
                    }
                    return true;
                })
                .map(legalTicketMapper::toMessageResponse)
                .collect(Collectors.toList());
    }
}
