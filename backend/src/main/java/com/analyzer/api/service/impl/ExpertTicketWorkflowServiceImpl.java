package com.analyzer.api.service.impl;

import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.*;
import com.analyzer.api.exception.common.*;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.legalticket.LegalTicketRepository;
import com.analyzer.api.repository.legalticket.ExpertTicketCreditReservationRepository;
import com.analyzer.api.repository.*;
import com.analyzer.api.service.*;
import com.analyzer.api.service.support.UserQuotaLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpertTicketWorkflowServiceImpl implements ExpertTicketWorkflowService {
    private final LegalTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ExpertTicketCreditReservationRepository creditRepository;
    private final SubscriptionQuotaService quotaService;
    private final UserQuotaLock userQuotaLock;
    private final TicketCollaborationService collaborationService;
    private final LegalTicketMapper mapper;
    private final ExpertRevenueService revenueService;

    @Override
    @Transactional
    public LegalTicketResponse classify(Long adminId, String ticketId, AdminTicketClassificationRequest request) {
        LegalTicket ticket = requireTicket(ticketId);
        requireState(ticket, LegalTicketStatus.PENDING_ADMIN_REVIEW, LegalTicketStatus.RECLASSIFICATION_REQUESTED);
        if (ticket.getTicketType() != LegalTicketType.CONTACT_EXPERT) {
            throw new ConflictException("EXPERT_CLASSIFICATION_NOT_APPLICABLE");
        }
        User admin = requireRole(adminId, RoleName.ADMIN);
        User expert = requireRole(request.getProposedExpertId(), RoleName.EXPERT);
        if (ticket.getCustomerPaymentStatus() == TicketPaymentStatus.PAID || ticket.getStartedAt() != null) {
            throw new ConflictException("TICKET_PRICING_LOCKED");
        }
        if (request.getPricingType() == TicketPricingType.PLAN_INCLUDED
                && request.getComplexity() != TicketComplexity.BASIC) {
            throw new ConflictException("INCLUDED_CREDIT_REQUIRES_BASIC_TICKET");
        }
        if (request.getPricingType() == TicketPricingType.PLAN_INCLUDED
                && request.getUserPrice().compareTo(BigDecimal.ZERO) != 0) {
            throw new ConflictException("INCLUDED_TICKET_USER_PRICE_MUST_BE_ZERO");
        }
        if (request.getPricingType() == TicketPricingType.PAID
                && request.getUserPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("PAID_TICKET_PRICE_REQUIRED");
        }
        LocalDateTime now = LocalDateTime.now();
        ticket.setTicketComplexity(request.getComplexity());
        ticket.setClassificationReason(request.getReason().trim());
        ticket.setClassifiedAt(now);
        ticket.setClassifiedBy(admin);
        ticket.setProposedExpert(expert);
        ticket.setPricingType(request.getPricingType());
        ticket.setUserPrice(request.getUserPrice());
        ticket.setInternalTicketValue(request.getInternalTicketValue());
        ticket.setQuoteStatus(TicketQuoteStatus.DRAFT);
        ticket.setCustomerPaymentStatus(request.getPricingType() == TicketPricingType.PLAN_INCLUDED
                ? TicketPaymentStatus.NOT_REQUIRED : TicketPaymentStatus.UNPAID);
        ticket.setStatus(LegalTicketStatus.PENDING_EXPERT_ASSESSMENT);
        LegalTicket saved = ticketRepository.save(ticket);
        audit(saved, admin, "ADMIN_CLASSIFIED", request.getReason());
        audit(saved, admin, "EXPERT_ASSESSMENT_REQUESTED", String.valueOf(expert.getId()));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LegalTicketResponse assess(Long expertId, String ticketId, ExpertTicketAssessmentRequest request) {
        LegalTicket ticket = requireTicket(ticketId);
        requireState(ticket, LegalTicketStatus.PENDING_EXPERT_ASSESSMENT);
        User expert = requireRole(expertId, RoleName.EXPERT);
        if (ticket.getProposedExpert() == null || !ticket.getProposedExpert().getId().equals(expertId)) {
            throw new ForbiddenException("NOT_PROPOSED_EXPERT");
        }
        String decision = request.getDecision();
        if (!"ACCEPT".equals(decision) && (request.getReason() == null || request.getReason().isBlank())) {
            throw new ConflictException("EXPERT_DECISION_REASON_REQUIRED");
        }
        if ("DECLINE".equals(decision)) {
            ticket.setStatus(LegalTicketStatus.PENDING_REASSIGNMENT);
            ticket.setProposedExpert(null);
            audit(ticket, expert, "EXPERT_DECLINED", request.getReason());
        } else if ("REQUEST_RECLASSIFICATION".equals(decision)) {
            if (request.getProposedType() == null || request.getExpectedDurationHours() == null) {
                throw new ConflictException("RECLASSIFICATION_DETAILS_REQUIRED");
            }
            ticket.setStatus(LegalTicketStatus.RECLASSIFICATION_REQUESTED);
            audit(ticket, expert, "RECLASSIFICATION_REQUESTED",
                    request.getProposedType() + ":" + request.getReason());
        } else {
            if (ticket.getPricingType() == TicketPricingType.PLAN_INCLUDED) reserveCredit(ticket);
            ticket.setQuoteStatus(TicketQuoteStatus.SENT);
            ticket.setStatus(LegalTicketStatus.WAITING_USER_ACCEPTANCE);
            audit(ticket, expert, "EXPERT_ACCEPTED", "assessment");
            audit(ticket, expert, "QUOTE_SENT", ticket.getUserPrice().toPlainString());
        }
        return expertResponse(ticketRepository.save(ticket));
    }

    @Override
    @Transactional
    public LegalTicketResponse decideQuote(Long customerId, String ticketId, TicketQuoteDecisionRequest request) {
        LegalTicket ticket = requireOwnedTicket(customerId, ticketId);
        requireState(ticket, LegalTicketStatus.WAITING_USER_ACCEPTANCE);
        if ("REJECT".equals(request.getDecision())) {
            ticket.setQuoteStatus(TicketQuoteStatus.REJECTED);
            ticket.setStatus(LegalTicketStatus.CANCELLED_BY_USER);
            releaseCredit(ticket, "QUOTE_REJECTED");
        } else {
            ticket.setQuoteStatus(TicketQuoteStatus.ACCEPTED);
            ticket.setStatus(ticket.getPricingType() == TicketPricingType.PLAN_INCLUDED
                    ? LegalTicketStatus.READY_FOR_ASSIGNMENT : LegalTicketStatus.WAITING_PAYMENT);
        }
        audit(ticket, ticket.getCreatedBy(), "QUOTE_" + request.getDecision(), "");
        return customerResponse(ticketRepository.save(ticket));
    }

    @Override
    @Transactional
    public LegalTicketResponse confirmPayment(Long adminId, String ticketId, TicketPaymentConfirmationRequest request) {
        User admin = requireRole(adminId, RoleName.ADMIN);
        LegalTicket ticket = requireTicket(ticketId);
        if (ticket.getCustomerPaymentStatus() == TicketPaymentStatus.PAID) return mapper.toResponse(ticket);
        requireState(ticket, LegalTicketStatus.WAITING_PAYMENT);
        if (ticket.getPricingType() != TicketPricingType.PAID || ticket.getQuoteStatus() != TicketQuoteStatus.ACCEPTED) {
            throw new ConflictException("PAYMENT_NOT_EXPECTED");
        }
        ticket.setCustomerPaymentStatus(TicketPaymentStatus.PAID);
        ticket.setCustomerPaymentReference(request.getPaymentReference().trim());
        ticket.setStatus(LegalTicketStatus.READY_FOR_ASSIGNMENT);
        audit(ticket, admin, "PAYMENT_CONFIRMED", request.getPaymentReference());
        return mapper.toResponse(ticketRepository.save(ticket));
    }

    @Override
    @Transactional
    public LegalTicketResponse offerAssignment(Long adminId, String ticketId, Long expertId, String reason) {
        User admin = requireRole(adminId, RoleName.ADMIN);
        User expert = requireRole(expertId, RoleName.EXPERT);
        LegalTicket ticket = requireTicket(ticketId);
        requireState(ticket, LegalTicketStatus.READY_FOR_ASSIGNMENT, LegalTicketStatus.PENDING_REASSIGNMENT);
        if (ticket.getPricingType() == TicketPricingType.PAID && ticket.getCustomerPaymentStatus() != TicketPaymentStatus.PAID) {
            throw new ConflictException("PAYMENT_REQUIRED_BEFORE_ASSIGNMENT");
        }
        if (ticket.getPricingType() == TicketPricingType.PLAN_INCLUDED
                && ticket.getQuotaReservationStatus() != TicketQuotaReservationStatus.RESERVED
                && ticket.getQuotaReservationStatus() != TicketQuotaReservationStatus.CONSUMED) {
            reserveCredit(ticket);
        }
        ticket.setProposedExpert(expert);
        ticket.setAssignmentOfferedAt(LocalDateTime.now());
        ticket.setAcceptanceDueAt(LocalDateTime.now().plusHours(12));
        ticket.setStatus(LegalTicketStatus.PENDING_EXPERT_ACCEPTANCE);
        audit(ticket, admin, "ASSIGNMENT_OFFERED", reason == null ? String.valueOf(expertId) : reason);
        return mapper.toResponse(ticketRepository.save(ticket));
    }

    @Override
    @Transactional
    public LegalTicketResponse reassign(Long adminId, String ticketId, Long expertId, String reason) {
        if (reason == null || reason.isBlank()) throw new ConflictException("REASSIGNMENT_REASON_REQUIRED");
        User admin = requireRole(adminId, RoleName.ADMIN);
        LegalTicket ticket = requireTicket(ticketId);
        if (List.of(LegalTicketStatus.RESOLVED, LegalTicketStatus.CLOSED,
                LegalTicketStatus.CANCELLED, LegalTicketStatus.CANCELLED_BY_USER,
                LegalTicketStatus.REFUNDED).contains(ticket.getStatus())) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }
        if (ticket.getAssignedLawyer() != null) {
            ticket.setPreviousExpertId(ticket.getAssignedLawyer().getId());
            ticket.setAssignedLawyer(null);
        }
        ticket.setProposedExpert(null);
        ticket.setReassignmentReason(reason.trim());
        ticket.setReassignedAt(LocalDateTime.now());
        ticket.setReassignedById(adminId);
        ticket.setStatus(LegalTicketStatus.PENDING_REASSIGNMENT);
        ticketRepository.save(ticket);
        audit(ticket, admin, "REASSIGNED", reason);
        return offerAssignment(adminId, ticketId, expertId, reason);
    }

    @Override
    @Transactional
    public LegalTicketResponse decideAssignment(Long expertId, String ticketId, ExpertAssignmentDecisionRequest request) {
        User expert = requireRole(expertId, RoleName.EXPERT);
        LegalTicket ticket = requireTicket(ticketId);
        requireState(ticket, LegalTicketStatus.PENDING_EXPERT_ACCEPTANCE);
        if (ticket.getProposedExpert() == null || !ticket.getProposedExpert().getId().equals(expertId)) {
            throw new ForbiddenException("NOT_PROPOSED_EXPERT");
        }
        if (ticket.getAcceptanceDueAt() != null && ticket.getAcceptanceDueAt().isBefore(LocalDateTime.now())) {
            ticket.setStatus(LegalTicketStatus.PENDING_REASSIGNMENT);
            releaseCredit(ticket, "ACCEPTANCE_EXPIRED");
            throw new ConflictException("ASSIGNMENT_ACCEPTANCE_EXPIRED");
        }
        if ("DECLINE".equals(request.getDecision())) {
            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new ConflictException("EXPERT_DECLINE_REASON_REQUIRED");
            }
            ticket.setStatus(LegalTicketStatus.PENDING_REASSIGNMENT);
            ticket.setProposedExpert(null);
            releaseCredit(ticket, "EXPERT_DECLINED");
            audit(ticket, expert, "EXPERT_DECLINED", request.getReason());
        } else {
            if (ticket.getPricingType() == TicketPricingType.PAID
                    && ticket.getCustomerPaymentStatus() != TicketPaymentStatus.PAID) {
                throw new ConflictException("PAYMENT_REQUIRED_BEFORE_EXPERT_ACCEPTANCE");
            }
            LocalDateTime now = LocalDateTime.now();
            ticket.setAssignedLawyer(expert);
            ticket.setAssignedAt(now);
            ticket.setAcceptedAt(now);
            ticket.setFirstResponseDueAt(now.plusHours(24));
            int days = ticket.getTicketComplexity() == TicketComplexity.BASIC ? 3
                    : ticket.getTicketComplexity() == TicketComplexity.STANDARD ? 5 : 7;
            ticket.setResolutionDueAt(now.plusDays(days));
            ticket.setLastExpertActivityAt(now);
            ticket.setSlaStatus(TicketSlaStatus.ON_TRACK);
            ticket.setStatus(LegalTicketStatus.ASSIGNED_TO_EXPERT);
            consumeCredit(ticket);
            revenueService.applyCommissionSnapshot(ticket);
            audit(ticket, expert, "EXPERT_ACCEPTED", "assignment");
        }
        return expertResponse(ticketRepository.save(ticket));
    }

    @Override
    @Transactional
    public LegalTicketResponse extendSla(Long adminId, String ticketId, AdminSlaExtensionRequest request) {
        User admin = requireRole(adminId, RoleName.ADMIN);
        LegalTicket ticket = requireTicket(ticketId);
        if (ticket.getAcceptedAt() == null || ticket.getResolutionDueAt() == null
                || List.of(LegalTicketStatus.RESOLVED, LegalTicketStatus.CLOSED,
                        LegalTicketStatus.CANCELLED_BY_USER, LegalTicketStatus.REFUNDED).contains(ticket.getStatus())) {
            throw new ConflictException("SLA_EXTENSION_NOT_ALLOWED");
        }
        ticket.setResolutionDueAt(ticket.getResolutionDueAt().plusHours(request.getHours()));
        ticket.setExtensionReason(request.getReason().trim());
        ticket.setSlaStatus(TicketSlaStatus.EXTENDED);
        audit(ticket, admin, "SLA_EXTENDED", request.getHours() + "h:" + request.getReason());
        return mapper.toResponse(ticketRepository.save(ticket));
    }

    private void reserveCredit(LegalTicket ticket) {
        User user = ticket.getCreatedBy();
        userQuotaLock.acquire(user.getId());
        var usage = quotaService.getCurrentUsage(user);
        int limit = usage.getExpertTicketsLimit() == null ? 0 : usage.getExpertTicketsLimit();
        String cycle = usage.getPeriodStart().toLocalDate() + ":" + usage.getPeriodEnd().toLocalDate();
        long used = creditRepository.countByUser_IdAndQuotaCycleAndStatusIn(user.getId(), cycle,
                List.of(TicketQuotaReservationStatus.RESERVED, TicketQuotaReservationStatus.CONSUMED));
        if (limit <= 0 || used >= limit) {
            throw new ConflictException("EXPERT_TICKET_CREDIT_UNAVAILABLE_REQUIRES_PAID_QUOTE");
        }
        ExpertTicketCreditReservation reservation = creditRepository.findByTicket_Id(ticket.getId())
                .orElseGet(() -> ExpertTicketCreditReservation.builder().ticket(ticket).user(user).build());
        reservation.setQuotaCycle(cycle);
        reservation.setStatus(TicketQuotaReservationStatus.RESERVED);
        reservation.setReservedAt(LocalDateTime.now());
        reservation.setReleasedAt(null);
        reservation.setReleaseReason(null);
        creditRepository.save(reservation);
        ticket.setQuotaCycle(cycle);
        ticket.setQuotaReservationStatus(TicketQuotaReservationStatus.RESERVED);
        audit(ticket, user, "CREDIT_RESERVED", cycle);
    }

    private void consumeCredit(LegalTicket ticket) {
        if (ticket.getPricingType() != TicketPricingType.PLAN_INCLUDED) return;
        ExpertTicketCreditReservation reservation = creditRepository.findByTicket_Id(ticket.getId())
                .orElseThrow(() -> new ConflictException("CREDIT_RESERVATION_NOT_FOUND"));
        if (reservation.getStatus() == TicketQuotaReservationStatus.CONSUMED) {
            ticket.setQuotaReservationStatus(TicketQuotaReservationStatus.CONSUMED);
            return;
        }
        reservation.setStatus(TicketQuotaReservationStatus.CONSUMED);
        reservation.setConsumedAt(LocalDateTime.now());
        ticket.setQuotaReservationStatus(TicketQuotaReservationStatus.CONSUMED);
        audit(ticket, ticket.getAssignedLawyer(), "CREDIT_CONSUMED", ticket.getQuotaCycle());
    }

    private void releaseCredit(LegalTicket ticket, String reason) {
        creditRepository.findByTicket_Id(ticket.getId()).ifPresent(reservation -> {
            if (reservation.getStatus() == TicketQuotaReservationStatus.RESERVED) {
                reservation.setStatus(TicketQuotaReservationStatus.RELEASED);
                reservation.setReleasedAt(LocalDateTime.now());
                reservation.setReleaseReason(reason);
                ticket.setQuotaReservationStatus(TicketQuotaReservationStatus.RELEASED);
                audit(ticket, ticket.getCreatedBy(), "CREDIT_RELEASED", reason);
            }
        });
    }

    private LegalTicket requireTicket(String id) {
        return ticketRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));
    }
    private LegalTicket requireOwnedTicket(Long userId, String id) {
        LegalTicket ticket = requireTicket(id);
        if (!ticket.getCreatedBy().getId().equals(userId)) throw new ForbiddenException("TICKET_ACCESS_DENIED");
        return ticket;
    }
    private User requireRole(Long id, RoleName role) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));
        if (!user.isActive() || user.getRole() == null || user.getRole().getName() != role) {
            throw new ForbiddenException("ROLE_REQUIRED_" + role.name());
        }
        return user;
    }
    private void requireState(LegalTicket ticket, LegalTicketStatus... allowed) {
        for (LegalTicketStatus status : allowed) if (ticket.getStatus() == status) return;
        throw new ConflictException("INVALID_STATUS_TRANSITION", "Action is not allowed from " + ticket.getStatus());
    }
    private void audit(LegalTicket ticket, User actor, String action, String reason) {
        collaborationService.auditTicket(ticket, actor, action,
                "{\"reason\":\"" + (reason == null ? "" : reason.replace("\"", "'")) + "\"}");
    }
    private LegalTicketResponse customerResponse(LegalTicket ticket) {
        LegalTicketResponse response = mapper.toResponse(ticket);
        response.setCommissionRate(null); response.setPlatformFee(null); response.setExpertPayout(null);
        response.setInternalTicketValue(null); response.setAdminNote(null); response.setExpertInternalNote(null);
        response.setApprovedPartialPayout(null); response.setContributionNote(null);
        return response;
    }
    private LegalTicketResponse expertResponse(LegalTicket ticket) {
        LegalTicketResponse response = mapper.toResponse(ticket);
        response.setCommissionRate(null); response.setPlatformFee(null); response.setInternalTicketValue(null);
        response.setAdminNote(null); response.setExpertInternalNote(null);
        boolean consentActive = ticket.getConsentRevokedAt() == null;
        String shared = ticket.getSharedProfileFieldsJson() == null ? "" : ticket.getSharedProfileFieldsJson();
        if (!consentActive || !shared.contains("\"DISPLAY_NAME\"")) response.setUserDisplayName(null);
        if (!consentActive || !shared.contains("\"EMAIL\"")) response.setUserEmail(null);
        response.setUserPhone(null);
        return response;
    }
}
