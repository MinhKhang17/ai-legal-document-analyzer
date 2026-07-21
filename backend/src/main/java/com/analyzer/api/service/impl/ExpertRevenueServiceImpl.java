package com.analyzer.api.service.impl;

import com.analyzer.api.dto.revenue.*;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.enums.ExpertPaymentStatus;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.service.ExpertRevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpertRevenueServiceImpl implements ExpertRevenueService {
    private final LegalTicketRepository ticketRepository;

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
                .totalRevenue(sum(tickets, null))
                .paidRevenue(sum(tickets, ExpertPaymentStatus.PAID))
                .pendingRevenue(sum(tickets, ExpertPaymentStatus.PENDING))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpertRevenueTicketResponse> getTickets(Long expertId) {
        return ticketRepository.findByAssignedLawyerIdAndDeletedFalse(expertId).stream()
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ExpertRevenueTicketResponse updatePayment(String ticketId, UpdateExpertPaymentRequest request) {
        LegalTicket ticket = ticketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        if (ticket.getAssignedLawyer() == null) {
            throw new ConflictException("Ticket has not been assigned to an expert");
        }
        ticket.setConsultationFee(request.getConsultationFee());
        ticket.setExpertPaymentStatus(request.getPaymentStatus());
        ticket.setExpertPaidAt(request.getPaymentStatus() == ExpertPaymentStatus.PAID
                ? LocalDateTime.now() : null);
        return toResponse(ticketRepository.save(ticket));
    }

    private BigDecimal sum(List<LegalTicket> tickets, ExpertPaymentStatus status) {
        return tickets.stream()
                .filter(this::isResolved)
                .filter(t -> status == null || t.getExpertPaymentStatus() == status)
                .map(t -> t.getConsultationFee() == null ? BigDecimal.ZERO : t.getConsultationFee())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isResolved(LegalTicket ticket) {
        return ticket.getStatus() == LegalTicketStatus.RESOLVED || ticket.getStatus() == LegalTicketStatus.CLOSED;
    }

    private ExpertRevenueTicketResponse toResponse(LegalTicket ticket) {
        return ExpertRevenueTicketResponse.builder().ticketId(ticket.getId()).ticketCode(ticket.getTicketCode())
                .ticketStatus(ticket.getStatus()).consultationFee(ticket.getConsultationFee())
                .paymentStatus(ticket.getExpertPaymentStatus()).resolvedAt(ticket.getResolvedAt())
                .paidAt(ticket.getExpertPaidAt()).build();
    }
}
