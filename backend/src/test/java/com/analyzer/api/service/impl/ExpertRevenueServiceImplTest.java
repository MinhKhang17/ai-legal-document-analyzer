package com.analyzer.api.service.impl;

import com.analyzer.api.dto.revenue.UpdateExpertPaymentRequest;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.ExpertPaymentStatus;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.TicketPricingType;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.RevenueSettingService;
import com.analyzer.api.service.TicketCollaborationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpertRevenueServiceImplTest {
    @Mock LegalTicketRepository repository;
    @Mock UserRepository userRepository;
    @Mock RevenueSettingService revenueSettingService;
    @Mock TicketCollaborationService collaborationService;

    private ExpertRevenueServiceImpl service() {
        return new ExpertRevenueServiceImpl(repository, userRepository, revenueSettingService, collaborationService);
    }

    @Test
    void summaryOnlyCountsResolvedFeesAsRevenue() {
        LegalTicket paid = ticket("paid", LegalTicketStatus.RESOLVED, "150000", ExpertPaymentStatus.PAID);
        LegalTicket pending = ticket("pending", LegalTicketStatus.CLOSED, "50000", ExpertPaymentStatus.PENDING);
        LegalTicket open = ticket("open", LegalTicketStatus.IN_REVIEW, "90000", ExpertPaymentStatus.PENDING);
        when(repository.findByAssignedLawyerIdAndDeletedFalse(9L)).thenReturn(List.of(paid, pending, open));

        var result = service().getSummary(9L);

        assertEquals(3, result.getAssignedTicketCount());
        assertEquals(2, result.getResolvedTicketCount());
        assertEquals(new BigDecimal("200000"), result.getTotalRevenue());
        assertEquals(new BigDecimal("150000"), result.getPaidRevenue());
        assertEquals(new BigDecimal("50000"), result.getPendingRevenue());
    }

    @Test
    void markingTicketPaidSnapshotsCommissionAndWritesAudit() {
        LegalTicket ticket = ticket("ticket", LegalTicketStatus.RESOLVED, "0", ExpertPaymentStatus.UNPAID);
        ticket.setAssignedLawyer(User.builder().id(9L).build());
        User admin = User.builder().id(1L).build();
        UpdateExpertPaymentRequest request = new UpdateExpertPaymentRequest();
        request.setConsultationFee(new BigDecimal("120000"));
        request.setPaymentStatus(ExpertPaymentStatus.PAID);
        when(repository.findByIdAndDeletedFalse("ticket")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(revenueSettingService.getCurrentRate()).thenReturn(new BigDecimal("0.2000"));
        when(repository.save(ticket)).thenReturn(ticket);

        var result = service().updatePayment("ticket", 1L, request);

        assertEquals(ExpertPaymentStatus.PAID, result.getPaymentStatus());
        assertEquals(new BigDecimal("120000"), result.getConsultationFee());
        assertEquals(new BigDecimal("0.2000"), result.getCommissionRate());
        assertEquals(new BigDecimal("24000.00"), result.getPlatformFee());
        assertEquals(new BigDecimal("96000.00"), result.getExpertPayout());
        assertNotNull(result.getPaidAt());
        verify(collaborationService).auditTicket(eq(ticket), eq(admin), eq("EXPERT_PAYMENT_UPDATED"), anyString());
    }

    @Test
    void updatePaymentRejectsTicketWithoutAssignedExpert() {
        LegalTicket ticket = ticket("ticket", LegalTicketStatus.IN_REVIEW, "0", ExpertPaymentStatus.UNPAID);
        UpdateExpertPaymentRequest request = new UpdateExpertPaymentRequest();
        request.setConsultationFee(new BigDecimal("50000"));
        request.setPaymentStatus(ExpertPaymentStatus.PENDING);
        when(repository.findByIdAndDeletedFalse("ticket")).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service().updatePayment("ticket", 1L, request));
        verifyNoInteractions(collaborationService);
    }

    @Test
    void resetFinancialsClearsAllFieldsWhenNotPaid() {
        LegalTicket ticket = ticket("ticket", LegalTicketStatus.RESOLVED, "500000", ExpertPaymentStatus.PENDING);
        ticket.setAssignedLawyer(User.builder().id(9L).build());
        ticket.setCommissionRate(new BigDecimal("0.2000"));
        ticket.setPlatformFee(new BigDecimal("100000.00"));
        ticket.setExpertPayout(new BigDecimal("400000.00"));
        User admin = User.builder().id(1L).build();
        when(repository.findByIdAndDeletedFalse("ticket")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.save(ticket)).thenReturn(ticket);

        var result = service().resetFinancials("ticket", 1L);

        assertEquals(BigDecimal.ZERO, result.getConsultationFee());
        assertEquals(ExpertPaymentStatus.UNPAID, result.getPaymentStatus());
        assertNull(result.getPaidAt());
        assertNull(ticket.getCommissionRate());
        assertNull(ticket.getPlatformFee());
        assertNull(ticket.getExpertPayout());
        verify(collaborationService).auditTicket(eq(ticket), eq(admin), eq("EXPERT_PAYMENT_RESET"), anyString());
    }

    @Test
    void resetFinancialsRejectsAlreadyPaidTicket() {
        LegalTicket ticket = ticket("ticket", LegalTicketStatus.RESOLVED, "500000", ExpertPaymentStatus.PAID);
        ticket.setAssignedLawyer(User.builder().id(9L).build());
        when(repository.findByIdAndDeletedFalse("ticket")).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service().resetFinancials("ticket", 1L));
        verifyNoInteractions(collaborationService);
        verify(repository, never()).save(any());
    }

    @Test
    void applyCommissionSnapshotComputesPlatformFeeAndPayout() {
        LegalTicket ticket = ticket("t1", LegalTicketStatus.RESOLVED, "1000000", ExpertPaymentStatus.UNPAID);
        ticket.setAssignedLawyer(User.builder().id(5L).build());
        when(revenueSettingService.getCurrentRate()).thenReturn(new BigDecimal("0.2000"));

        service().applyCommissionSnapshot(ticket);

        assertEquals(new BigDecimal("0.2000"), ticket.getCommissionRate());
        assertEquals(new BigDecimal("200000.00"), ticket.getPlatformFee());
        assertEquals(new BigDecimal("800000.00"), ticket.getExpertPayout());
    }

    @Test
    void includedTicketPayoutUsesInternalValueInsteadOfZeroUserPrice() {
        LegalTicket ticket = ticket("included", LegalTicketStatus.ASSIGNED_TO_EXPERT, "0", ExpertPaymentStatus.UNPAID);
        ticket.setAssignedLawyer(User.builder().id(5L).build());
        ticket.setPricingType(TicketPricingType.PLAN_INCLUDED);
        ticket.setUserPrice(BigDecimal.ZERO);
        ticket.setInternalTicketValue(new BigDecimal("500000"));
        when(revenueSettingService.getCurrentRate()).thenReturn(new BigDecimal("0.2000"));

        service().applyCommissionSnapshot(ticket);

        assertEquals(new BigDecimal("500000"), ticket.getConsultationFee());
        assertEquals(new BigDecimal("100000.00"), ticket.getPlatformFee());
        assertEquals(new BigDecimal("400000.00"), ticket.getExpertPayout());
    }

    @Test
    void applyCommissionSnapshotSkipsTicketsWithoutAssignedExpert() {
        LegalTicket ticket = ticket("t1", LegalTicketStatus.RESOLVED, "1000000", ExpertPaymentStatus.UNPAID);

        service().applyCommissionSnapshot(ticket);

        assertNull(ticket.getCommissionRate());
        assertNull(ticket.getPlatformFee());
        verifyNoInteractions(revenueSettingService);
    }

    @Test
    void applyCommissionSnapshotKeepsAlreadyLockedRate() {
        LegalTicket ticket = ticket("t1", LegalTicketStatus.RESOLVED, "1000000", ExpertPaymentStatus.UNPAID);
        ticket.setAssignedLawyer(User.builder().id(5L).build());
        ticket.setCommissionRate(new BigDecimal("0.1000"));

        service().applyCommissionSnapshot(ticket);

        assertEquals(new BigDecimal("0.1000"), ticket.getCommissionRate());
        assertEquals(new BigDecimal("100000.00"), ticket.getPlatformFee());
        verifyNoInteractions(revenueSettingService);
    }

    @Test
    void getTicketsReturnsPagedResponse() {
        LegalTicket t = ticket("t1", LegalTicketStatus.RESOLVED, "100000", ExpertPaymentStatus.PAID);
        Page<LegalTicket> page = new PageImpl<>(List.of(t), PageRequest.of(0, 10), 1);
        when(repository.findByAssignedLawyerIdAndDeletedFalse(eq(9L), any(Pageable.class))).thenReturn(page);

        var result = service().getTickets(9L, 0, 10);

        assertEquals(1, result.getItems().size());
        assertEquals(1, result.getTotalItems());
        assertEquals(1, result.getTotalPages());
    }

    @Test
    void adminOverviewAggregatesAcrossAllExperts() {
        User expertA = User.builder().id(1L).firstName("Nguyen").lastName("A").build();
        User expertB = User.builder().id(2L).firstName("Tran").lastName("B").build();
        LegalTicket t1 = ticket("t1", LegalTicketStatus.RESOLVED, "100000", ExpertPaymentStatus.PAID);
        t1.setAssignedLawyer(expertA);
        t1.setPlatformFee(new BigDecimal("20000"));
        t1.setExpertPayout(new BigDecimal("80000"));
        LegalTicket t2 = ticket("t2", LegalTicketStatus.CLOSED, "200000", ExpertPaymentStatus.PENDING);
        t2.setAssignedLawyer(expertA);
        t2.setPlatformFee(new BigDecimal("40000"));
        t2.setExpertPayout(new BigDecimal("160000"));
        LegalTicket t3 = ticket("t3", LegalTicketStatus.RESOLVED, "50000", ExpertPaymentStatus.PAID);
        t3.setAssignedLawyer(expertB);
        t3.setPlatformFee(new BigDecimal("10000"));
        t3.setExpertPayout(new BigDecimal("40000"));
        when(repository.findByStatusInAndAssignedLawyerIsNotNullAndDeletedFalse(
                List.of(LegalTicketStatus.RESOLVED, LegalTicketStatus.CLOSED)))
                .thenReturn(List.of(t1, t2, t3));

        var result = service().getAdminOverview();

        assertEquals(3, result.getTotalTicketCount());
        assertEquals(2, result.getPaidTicketCount());
        assertEquals(1, result.getPendingPaymentTicketCount());
        assertEquals(new BigDecimal("350000"), result.getTotalConsultationFee());
        assertEquals(new BigDecimal("70000"), result.getTotalPlatformFee());
        assertEquals(new BigDecimal("280000"), result.getTotalExpertPayout());
        assertEquals(2, result.getByExpert().size());
    }

    private LegalTicket ticket(String id, LegalTicketStatus status, String fee, ExpertPaymentStatus paymentStatus) {
        return LegalTicket.builder().id(id).status(status).consultationFee(new BigDecimal(fee))
                .expertPaymentStatus(paymentStatus).build();
    }
}
