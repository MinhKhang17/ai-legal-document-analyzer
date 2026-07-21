package com.analyzer.api.service.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.*;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.ExpertPaymentStatus;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.ExpertRevenueService;
import com.analyzer.api.service.RevenueSettingService;
import com.analyzer.api.service.TicketCollaborationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpertRevenueServiceImpl implements ExpertRevenueService {
    private final LegalTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final RevenueSettingService revenueSettingService;
    private final TicketCollaborationService collaborationService;

    @Override
    @Transactional(readOnly = true)
    public ExpertRevenueSummaryResponse getSummary(Long expertId) {
        List<LegalTicket> tickets = ticketRepository.findByAssignedLawyerIdAndDeletedFalse(expertId);
        return ExpertRevenueSummaryResponse.builder()
                .assignedTicketCount(tickets.size())
                .resolvedTicketCount(tickets.stream().filter(this::isResolved).count())
                .paidTicketCount(tickets.stream().filter(this::isResolved)
                        .filter(t -> t.getExpertPaymentStatus() == ExpertPaymentStatus.PAID).count())
                .pendingPaymentTicketCount(tickets.stream().filter(this::isResolved)
                        .filter(t -> t.getExpertPaymentStatus() == ExpertPaymentStatus.PENDING).count())
                .totalRevenue(sum(tickets, null, LegalTicket::getConsultationFee))
                .paidRevenue(sum(tickets, ExpertPaymentStatus.PAID, LegalTicket::getConsultationFee))
                .pendingRevenue(sum(tickets, ExpertPaymentStatus.PENDING, LegalTicket::getConsultationFee))
                .totalPlatformFee(sum(tickets, null, LegalTicket::getPlatformFee))
                .totalExpertPayout(sum(tickets, null, LegalTicket::getExpertPayout))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExpertRevenueTicketResponse> getTickets(Long expertId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LegalTicket> pageResult = ticketRepository.findByAssignedLawyerIdAndDeletedFalse(expertId, pageable);

        return PageResponse.<ExpertRevenueTicketResponse>builder()
                .items(pageResult.getContent().stream().map(this::toResponse).collect(Collectors.toList()))
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalItems(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public ExpertRevenueTicketResponse updatePayment(String ticketId, Long adminId, UpdateExpertPaymentRequest request) {
        LegalTicket ticket = ticketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        if (ticket.getAssignedLawyer() == null) {
            throw new ConflictException("Ticket has not been assigned to an expert");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));

        ticket.setConsultationFee(request.getConsultationFee());
        ticket.setExpertPaymentStatus(request.getPaymentStatus());
        ticket.setExpertPaidAt(request.getPaymentStatus() == ExpertPaymentStatus.PAID
                ? LocalDateTime.now() : null);
        applyCommissionSnapshot(ticket);

        LegalTicket saved = ticketRepository.save(ticket);
        collaborationService.auditTicket(saved, admin, "EXPERT_PAYMENT_UPDATED",
                "{\"consultationFee\":\"" + request.getConsultationFee() + "\",\"paymentStatus\":\""
                        + request.getPaymentStatus() + "\"}");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ExpertRevenueTicketResponse resetFinancials(String ticketId, Long adminId) {
        LegalTicket ticket = ticketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        if (ticket.getExpertPaymentStatus() == ExpertPaymentStatus.PAID) {
            throw new ConflictException("CANNOT_RESET_PAID_TICKET");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));

        ticket.setConsultationFee(BigDecimal.ZERO);
        ticket.setExpertPaymentStatus(ExpertPaymentStatus.UNPAID);
        ticket.setExpertPaidAt(null);
        ticket.setCommissionRate(null);
        ticket.setPlatformFee(null);
        ticket.setExpertPayout(null);

        LegalTicket saved = ticketRepository.save(ticket);
        collaborationService.auditTicket(saved, admin, "EXPERT_PAYMENT_RESET", "{}");
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRevenueOverviewResponse getAdminOverview() {
        List<LegalTicket> tickets = ticketRepository.findByStatusInAndAssignedLawyerIsNotNullAndDeletedFalse(
                List.of(LegalTicketStatus.RESOLVED, LegalTicketStatus.CLOSED));

        var byExpert = tickets.stream()
                .collect(Collectors.groupingBy(t -> t.getAssignedLawyer().getId()))
                .entrySet().stream()
                .map(entry -> {
                    List<LegalTicket> expertTickets = entry.getValue();
                    User expert = expertTickets.get(0).getAssignedLawyer();
                    return ExpertRevenueBreakdownItem.builder()
                            .expertId(expert.getId())
                            .expertName((expert.getFirstName() + " " + expert.getLastName()).trim())
                            .ticketCount(expertTickets.size())
                            .totalConsultationFee(sum(expertTickets, null, LegalTicket::getConsultationFee))
                            .totalExpertPayout(sum(expertTickets, null, LegalTicket::getExpertPayout))
                            .build();
                })
                .collect(Collectors.toList());

        return AdminRevenueOverviewResponse.builder()
                .totalTicketCount(tickets.size())
                .paidTicketCount(tickets.stream().filter(t -> t.getExpertPaymentStatus() == ExpertPaymentStatus.PAID).count())
                .pendingPaymentTicketCount(tickets.stream().filter(t -> t.getExpertPaymentStatus() == ExpertPaymentStatus.PENDING).count())
                .totalConsultationFee(sum(tickets, null, LegalTicket::getConsultationFee))
                .totalPlatformFee(sum(tickets, null, LegalTicket::getPlatformFee))
                .totalExpertPayout(sum(tickets, null, LegalTicket::getExpertPayout))
                .byExpert(byExpert)
                .build();
    }

    @Override
    public void applyCommissionSnapshot(LegalTicket ticket) {
        if (ticket.getAssignedLawyer() == null) {
            return;
        }
        if (ticket.getCommissionRate() == null) {
            ticket.setCommissionRate(revenueSettingService.getCurrentRate());
        }
        BigDecimal fee = ticket.getConsultationFee() == null ? BigDecimal.ZERO : ticket.getConsultationFee();
        BigDecimal platformFee = fee.multiply(ticket.getCommissionRate()).setScale(2, RoundingMode.HALF_UP);
        ticket.setPlatformFee(platformFee);
        ticket.setExpertPayout(fee.subtract(platformFee));
    }

    private BigDecimal sum(List<LegalTicket> tickets, ExpertPaymentStatus status,
                            java.util.function.Function<LegalTicket, BigDecimal> extractor) {
        return tickets.stream()
                .filter(this::isResolved)
                .filter(t -> status == null || t.getExpertPaymentStatus() == status)
                .map(extractor)
                .map(value -> value == null ? BigDecimal.ZERO : value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isResolved(LegalTicket ticket) {
        return ticket.getStatus() == LegalTicketStatus.RESOLVED || ticket.getStatus() == LegalTicketStatus.CLOSED;
    }

    private ExpertRevenueTicketResponse toResponse(LegalTicket ticket) {
        return ExpertRevenueTicketResponse.builder().ticketId(ticket.getId()).ticketCode(ticket.getTicketCode())
                .ticketStatus(ticket.getStatus()).consultationFee(ticket.getConsultationFee())
                .commissionRate(ticket.getCommissionRate())
                .platformFee(ticket.getPlatformFee())
                .expertPayout(ticket.getExpertPayout())
                .paymentStatus(ticket.getExpertPaymentStatus()).resolvedAt(ticket.getResolvedAt())
                .paidAt(ticket.getExpertPaidAt()).build();
    }
}
