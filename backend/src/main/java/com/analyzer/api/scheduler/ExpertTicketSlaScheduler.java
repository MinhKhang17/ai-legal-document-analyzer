package com.analyzer.api.scheduler;

import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.enums.*;
import com.analyzer.api.repository.legalticket.ExpertTicketCreditReservationRepository;
import com.analyzer.api.repository.legalticket.LegalTicketRepository;
import com.analyzer.api.service.EmailService;
import com.analyzer.api.service.legalticket.TicketCollaborationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExpertTicketSlaScheduler {
    private static final Logger log = LoggerFactory.getLogger(ExpertTicketSlaScheduler.class);
    private final LegalTicketRepository ticketRepository;
    private final ExpertTicketCreditReservationRepository creditRepository;
    private final TicketCollaborationService collaborationService;
    private final EmailService emailService;

    @Scheduled(fixedDelayString = "${app.ticket-sla.scan-delay-ms:300000}")
    @Transactional
    public void updateDeadlines() {
        LocalDateTime now = LocalDateTime.now();
        List<LegalTicket> tickets = ticketRepository.findByStatusInAndDeletedFalse(List.of(
                LegalTicketStatus.PENDING_EXPERT_ACCEPTANCE, LegalTicketStatus.ASSIGNED_TO_EXPERT,
                LegalTicketStatus.IN_REVIEW, LegalTicketStatus.CUSTOMER_RESPONDED,
                LegalTicketStatus.INACTIVE_WARNING, LegalTicketStatus.OVERDUE));
        for (LegalTicket ticket : tickets) {
            if (ticket.getStatus() == LegalTicketStatus.PENDING_EXPERT_ACCEPTANCE
                    && ticket.getAcceptanceDueAt() != null && ticket.getAcceptanceDueAt().isBefore(now)) {
                ticket.setStatus(LegalTicketStatus.PENDING_REASSIGNMENT);
                ticket.setProposedExpert(null);
                releaseCredit(ticket, "ACCEPTANCE_EXPIRED", now);
                collaborationService.auditTicket(ticket, null, "EXPERT_ACCEPTANCE_EXPIRED", "{}");
                notifyCustomer(ticket, "Chuyên gia chưa phản hồi đề nghị đúng hạn; ticket đang được phân công lại.");
                continue;
            }
            if (ticket.getSlaStatus() == TicketSlaStatus.WAITING_FOR_USER)
                continue;
            LocalDateTime activity = ticket.getLastExpertActivityAt() != null
                    ? ticket.getLastExpertActivityAt()
                    : ticket.getAcceptedAt();
            if (activity != null && activity.plusHours(24).isBefore(now)
                    && ticket.getStatus() != LegalTicketStatus.INACTIVE_WARNING
                    && ticket.getStatus() != LegalTicketStatus.OVERDUE
                    && ticket.getStatus() != LegalTicketStatus.SLA_BREACHED) {
                ticket.setStatus(LegalTicketStatus.INACTIVE_WARNING);
                ticket.setSlaStatus(TicketSlaStatus.WARNING);
                collaborationService.auditTicket(ticket, null, "SLA_WARNING", "{\"reason\":\"NO_EXPERT_ACTIVITY\"}");
            }
            boolean firstResponseLate = ticket.getFirstRespondedAt() == null && ticket.getFirstResponseDueAt() != null
                    && ticket.getFirstResponseDueAt().isBefore(now);
            boolean resolutionLate = ticket.getResolvedAt() == null && ticket.getResolutionDueAt() != null
                    && ticket.getResolutionDueAt().isBefore(now);
            if (firstResponseLate || resolutionLate) {
                if (ticket.getStatus() == LegalTicketStatus.OVERDUE) {
                    ticket.setStatus(LegalTicketStatus.SLA_BREACHED);
                    ticket.setSlaStatus(TicketSlaStatus.BREACHED);
                    collaborationService.auditTicket(ticket, null, "SLA_BREACHED", "{}");
                    notifyCustomer(ticket,
                            "Thời gian dự kiến đã bị vượt; Admin đang can thiệp để bảo vệ quyền lợi của bạn.");
                } else {
                    ticket.setStatus(LegalTicketStatus.OVERDUE);
                    ticket.setSlaStatus(TicketSlaStatus.OVERDUE);
                    collaborationService.auditTicket(ticket, null, "SLA_OVERDUE", "{}");
                }
            }
        }
        log.debug("Processed {} active expert tickets for SLA", tickets.size());
    }

    private void releaseCredit(LegalTicket ticket, String reason, LocalDateTime now) {
        creditRepository.findByTicket_Id(ticket.getId()).ifPresent(reservation -> {
            if (reservation.getStatus() == TicketQuotaReservationStatus.RESERVED) {
                reservation.setStatus(TicketQuotaReservationStatus.RELEASED);
                reservation.setReleasedAt(now);
                reservation.setReleaseReason(reason);
                ticket.setQuotaReservationStatus(TicketQuotaReservationStatus.RELEASED);
            }
        });
    }

    private void notifyCustomer(LegalTicket ticket, String message) {
        try {
            emailService.sendTicketNotificationAsync(ticket.getCreatedBy().getEmail(),
                    ticket.getCreatedBy().getFirstName(), ticket.getId(), ticket.getTicketType().name(),
                    ticket.getStatus().name(), "/tickets/" + ticket.getId(), message);
        } catch (RuntimeException ex) {
            log.warn("Unable to enqueue SLA notification for ticket {}", ticket.getId(), ex);
        }
    }
}
