package com.analyzer.api.service.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.dto.legalticket.RequestMoreInfoRequest;
import com.analyzer.api.dto.legalticket.ResolveLegalTicketRequest;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.LegalTicketMessage;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.LegalTicketMessageType;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.LegalTicketMessageRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.ExpertLegalTicketService;
import com.analyzer.api.service.ExpertRevenueService;
import com.analyzer.api.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpertLegalTicketServiceImpl implements ExpertLegalTicketService {

    private final LegalTicketRepository legalTicketRepository;
    private final LegalTicketMessageRepository legalTicketMessageRepository;
    private final UserRepository userRepository;
    private final LegalTicketMapper legalTicketMapper;
    private final EmailService emailService;
    private final ExpertRevenueService expertRevenueService;
    private final RevenuePayrollService revenuePayrollService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LegalTicketResponse> getAssignedTickets(Long expertId, LegalTicketStatus status, int page, int size) {
        // Validate expert exists
        User expert = userRepository.findById(expertId)
                .orElseThrow(() -> new ResourceNotFoundException("EXPERT_NOT_FOUND"));

        if (expert.getRole() == null || expert.getRole().getName() != RoleName.EXPERT) {
            throw new ForbiddenException("Bạn không phải chuyên gia luật");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LegalTicket> pageResult;

        if (status != null) {
            pageResult = legalTicketRepository.findByAssignedLawyerIdAndStatusAndDeletedFalse(expertId, status, pageable);
        } else {
            pageResult = legalTicketRepository.findByAssignedLawyerIdAndDeletedFalse(expertId, pageable);
        }

        List<LegalTicketResponse> list = pageResult.getContent().stream()
                .map(this::toExpertResponse)
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
    @Transactional(readOnly = true)
    public PageResponse<LegalTicketResponse> getProposedTickets(Long expertId, int page, int size) {
        requireExpert(expertId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LegalTicket> result = legalTicketRepository.findByProposedExpertIdAndStatusInAndDeletedFalse(
                expertId, List.of(LegalTicketStatus.PENDING_EXPERT_ASSESSMENT,
                        LegalTicketStatus.PENDING_EXPERT_ACCEPTANCE), pageable);
        List<LegalTicketResponse> items = result.getContent().stream().map(this::toExpertResponse).toList();
        return PageResponse.<LegalTicketResponse>builder().items(items).page(result.getNumber()).size(result.getSize())
                .totalItems(result.getTotalElements()).totalPages(result.getTotalPages()).build();
    }

    private User requireExpert(Long expertId) {
        User expert = userRepository.findById(expertId)
                .orElseThrow(() -> new ResourceNotFoundException("EXPERT_NOT_FOUND"));
        if (expert.getRole() == null || expert.getRole().getName() != RoleName.EXPERT) {
            throw new ForbiddenException("EXPERT_ROLE_REQUIRED");
        }
        return expert;
    }

    private LegalTicketResponse toExpertResponse(LegalTicket ticket) {
        LegalTicketResponse response = legalTicketMapper.toResponse(ticket);
        response.setAdminNote(null); response.setExpertInternalNote(null);
        response.setCommissionRate(null); response.setPlatformFee(null); response.setInternalTicketValue(null);
        boolean consentActive = ticket.getConsentRevokedAt() == null;
        String shared = ticket.getSharedProfileFieldsJson() == null ? "" : ticket.getSharedProfileFieldsJson();
        if (!consentActive || !shared.contains("\"DISPLAY_NAME\"")) response.setUserDisplayName(null);
        if (!consentActive || !shared.contains("\"EMAIL\"")) response.setUserEmail(null);
        response.setUserPhone(null);
        return response;
    }

    @Override
    @Transactional
    public LegalTicketResponse startReview(Long expertId, String ticketId) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        validateAssignedExpert(ticket, expertId);

        if (ticket.getStatus() != LegalTicketStatus.ASSIGNED_TO_EXPERT &&
            ticket.getStatus() != LegalTicketStatus.ASSIGNED_TO_LAWYER &&
            ticket.getStatus() != LegalTicketStatus.CUSTOMER_RESPONDED &&
            ticket.getStatus() != LegalTicketStatus.REOPENED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        if (ticket.getPricingType() == com.analyzer.api.enums.TicketPricingType.PAID
                && ticket.getCustomerPaymentStatus() != com.analyzer.api.enums.TicketPaymentStatus.PAID) {
            throw new ConflictException("PAYMENT_REQUIRED_BEFORE_EXPERT_START");
        }
        ticket.setStatus(LegalTicketStatus.IN_REVIEW);
        if (ticket.getStartedAt() == null) ticket.setStartedAt(LocalDateTime.now());
        ticket.setLastExpertActivityAt(LocalDateTime.now());
        ticket.setLastLawyerMessageAt(LocalDateTime.now());

        // System message
        LegalTicketMessage systemMsg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(ticket.getAssignedLawyer())
                .content("Chuyên gia đã bắt đầu xử lý rà soát tài liệu.")
                .messageType(LegalTicketMessageType.SYSTEM)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(systemMsg);

        emailService.sendTicketNotificationAsync(ticket.getCreatedBy().getEmail(), ticket.getCreatedBy().getFirstName(),
                ticket.getId(), ticket.getTicketType() != null ? ticket.getTicketType().name() : "CONTACT_EXPERT",
                ticket.getStatus().name(), "/tickets/" + ticket.getId(), "Chuyen gia da giai quyet ticket.");

        return legalTicketMapper.toResponse(ticket);
    }

    @Override
    @Transactional
    public LegalTicketResponse requestMoreInfo(Long expertId, String ticketId, RequestMoreInfoRequest request) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        validateAssignedExpert(ticket, expertId);

        if (ticket.getStatus() != LegalTicketStatus.ASSIGNED_TO_LAWYER &&
            ticket.getStatus() != LegalTicketStatus.IN_REVIEW &&
            ticket.getStatus() != LegalTicketStatus.CUSTOMER_RESPONDED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        ticket.setStatus(LegalTicketStatus.NEED_MORE_INFO);
        ticket.setLastLawyerMessageAt(LocalDateTime.now());
        ticket.setLastExpertActivityAt(LocalDateTime.now());
        ticket.setPausedAt(LocalDateTime.now());
        ticket.setSlaStatus(com.analyzer.api.enums.TicketSlaStatus.WAITING_FOR_USER);

        // Save Request message
        LegalTicketMessage msg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(ticket.getAssignedLawyer())
                .content(request.getMessage())
                .messageType(LegalTicketMessageType.EXPERT_REQUEST_MORE_INFO)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(msg);

        return legalTicketMapper.toResponse(ticket);
    }

    @Override
    @Transactional
    public LegalTicketResponse resolveTicket(Long expertId, String ticketId, ResolveLegalTicketRequest request) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        validateAssignedExpert(ticket, expertId);

        if (ticket.getStatus() != LegalTicketStatus.IN_REVIEW &&
            ticket.getStatus() != LegalTicketStatus.CUSTOMER_RESPONDED &&
            ticket.getStatus() != LegalTicketStatus.REOPENED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        ticket.setStatus(LegalTicketStatus.RESOLVED);
        ticket.setExpertAnswer(request.getExpertAnswer());
        ticket.setExpertInternalNote(request.getExpertInternalNote());
        ticket.setResolvedAt(LocalDateTime.now());
        ticket.setCompletedAt(LocalDateTime.now());
        ticket.setLastLawyerMessageAt(LocalDateTime.now());
        ticket.setLastExpertActivityAt(LocalDateTime.now());
        expertRevenueService.applyCommissionSnapshot(ticket);
        revenuePayrollService.recognizeResolvedTicket(ticket);

        // Save Response Message
        LegalTicketMessage msg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(ticket.getAssignedLawyer())
                .content(request.getExpertAnswer())
                .messageType(LegalTicketMessageType.EXPERT_RESPONSE)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(msg);

        // Save Internal Note as private message if present
        if (request.getExpertInternalNote() != null && !request.getExpertInternalNote().isBlank()) {
            LegalTicketMessage noteMsg = LegalTicketMessage.builder()
                    .ticket(ticket)
                    .sender(ticket.getAssignedLawyer())
                    .content(request.getExpertInternalNote())
                    .messageType(LegalTicketMessageType.ADMIN_NOTE)
                    .internalOnly(true)
                    .build();
            legalTicketMessageRepository.save(noteMsg);
        }

        emailService.sendTicketNotificationAsync(ticket.getCreatedBy().getEmail(), ticket.getCreatedBy().getFirstName(),
                ticket.getId(), ticket.getTicketType() != null ? ticket.getTicketType().name() : "CONTACT_EXPERT",
                ticket.getStatus().name(), "/tickets/" + ticket.getId(), "Chuyen gia da giai quyet ticket.");

        return legalTicketMapper.toResponse(ticket);
    }

    private void validateAssignedExpert(LegalTicket ticket, Long expertId) {
        if (ticket.getAssignedLawyer() == null || !ticket.getAssignedLawyer().getId().equals(expertId)) {
            throw new ForbiddenException("NOT_ASSIGNED_EXPERT");
        }
    }
}
