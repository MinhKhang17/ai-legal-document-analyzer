package com.analyzer.api.service.impl;

import com.analyzer.api.dto.revenue.UpdateExpertPaymentRequest;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.ExpertPaymentStatus;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.repository.LegalTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpertRevenueServiceImplTest {
    @Mock LegalTicketRepository repository;

    @Test
    void summaryOnlyCountsResolvedFeesAsRevenue() {
        LegalTicket paid = ticket("paid", LegalTicketStatus.RESOLVED, "150000", ExpertPaymentStatus.PAID);
        LegalTicket pending = ticket("pending", LegalTicketStatus.CLOSED, "50000", ExpertPaymentStatus.PENDING);
        LegalTicket open = ticket("open", LegalTicketStatus.IN_REVIEW, "90000", ExpertPaymentStatus.PENDING);
        when(repository.findByAssignedLawyerIdAndDeletedFalse(9L)).thenReturn(List.of(paid, pending, open));

        var result = new ExpertRevenueServiceImpl(repository).getSummary(9L);

        assertEquals(3, result.getAssignedTicketCount());
        assertEquals(2, result.getResolvedTicketCount());
        assertEquals(new BigDecimal("200000"), result.getTotalRevenue());
        assertEquals(new BigDecimal("150000"), result.getPaidRevenue());
        assertEquals(new BigDecimal("50000"), result.getPendingRevenue());
    }

    @Test
    void markingTicketPaidSetsPaidTimestamp() {
        LegalTicket ticket = ticket("ticket", LegalTicketStatus.RESOLVED, "0", ExpertPaymentStatus.UNPAID);
        ticket.setAssignedLawyer(User.builder().id(9L).build());
        UpdateExpertPaymentRequest request = new UpdateExpertPaymentRequest();
        request.setConsultationFee(new BigDecimal("120000"));
        request.setPaymentStatus(ExpertPaymentStatus.PAID);
        when(repository.findByIdAndDeletedFalse("ticket")).thenReturn(Optional.of(ticket));
        when(repository.save(ticket)).thenReturn(ticket);

        var result = new ExpertRevenueServiceImpl(repository).updatePayment("ticket", request);

        assertEquals(ExpertPaymentStatus.PAID, result.getPaymentStatus());
        assertEquals(new BigDecimal("120000"), result.getConsultationFee());
        assertNotNull(result.getPaidAt());
    }

    private LegalTicket ticket(String id, LegalTicketStatus status, String fee, ExpertPaymentStatus paymentStatus) {
        return LegalTicket.builder().id(id).status(status).consultationFee(new BigDecimal(fee))
                .expertPaymentStatus(paymentStatus).build();
    }
}
