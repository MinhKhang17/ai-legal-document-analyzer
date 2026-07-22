package com.analyzer.api.service.impl;

import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.*;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.*;
import com.analyzer.api.service.*;
import com.analyzer.api.service.support.UserQuotaLock;
import com.analyzer.api.dto.subscription.SubscriptionQuotaUsageSummaryResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpertTicketWorkflowServiceImplTest {
    @Mock LegalTicketRepository ticketRepository;
    @Mock UserRepository userRepository;
    @Mock ExpertTicketCreditReservationRepository creditRepository;
    @Mock SubscriptionQuotaService quotaService;
    @Mock UserQuotaLock userQuotaLock;
    @Mock TicketCollaborationService collaborationService;
    @Mock LegalTicketMapper mapper;
    @Mock ExpertRevenueService revenueService;
    ExpertTicketWorkflowServiceImpl service;

    @BeforeEach void setUp() {
        service = new ExpertTicketWorkflowServiceImpl(ticketRepository, userRepository, creditRepository,
                quotaService, userQuotaLock, collaborationService, mapper, revenueService);
        lenient().when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(mapper.toResponse(any())).thenReturn(new LegalTicketResponse());
    }

    @Test
    void adminClassificationRequiresExpertAssessmentBeforeQuote() {
        User admin = user(1L, RoleName.ADMIN);
        User expert = user(2L, RoleName.EXPERT);
        LegalTicket ticket = ticket(LegalTicketStatus.PENDING_ADMIN_REVIEW);
        when(ticketRepository.findByIdAndDeletedFalse("ticket-1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(expert));

        service.classify(1L, "ticket-1", AdminTicketClassificationRequest.builder()
                .complexity(TicketComplexity.BASIC).reason("Eligible basic consultation")
                .proposedExpertId(2L).pricingType(TicketPricingType.PLAN_INCLUDED)
                .userPrice(BigDecimal.ZERO).internalTicketValue(new BigDecimal("500000")).build());

        assertThat(ticket.getStatus()).isEqualTo(LegalTicketStatus.PENDING_EXPERT_ASSESSMENT);
        assertThat(ticket.getQuoteStatus()).isEqualTo(TicketQuoteStatus.DRAFT);
        assertThat(ticket.getProposedExpert()).isSameAs(expert);
        verify(collaborationService).auditTicket(ticket, admin, "ADMIN_CLASSIFIED", "{\"reason\":\"Eligible basic consultation\"}");
    }

    @Test
    void paidAssignmentCannotBeAcceptedBeforePayment() {
        User expert = user(2L, RoleName.EXPERT);
        LegalTicket ticket = ticket(LegalTicketStatus.PENDING_EXPERT_ACCEPTANCE);
        ticket.setProposedExpert(expert);
        ticket.setPricingType(TicketPricingType.PAID);
        ticket.setCustomerPaymentStatus(TicketPaymentStatus.UNPAID);
        ticket.setAcceptanceDueAt(LocalDateTime.now().plusHours(1));
        when(ticketRepository.findByIdAndDeletedFalse("ticket-1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(expert));

        assertThatThrownBy(() -> service.decideAssignment(2L, "ticket-1",
                ExpertAssignmentDecisionRequest.builder().decision("ACCEPT").build()))
                .isInstanceOf(ConflictException.class);
        assertThat(ticket.getAssignedLawyer()).isNull();
    }

    @Test
    void exhaustedIncludedCreditReturnsTicketForPaidReclassification() {
        User expert = user(2L, RoleName.EXPERT);
        LegalTicket ticket = ticket(LegalTicketStatus.PENDING_EXPERT_ASSESSMENT);
        ticket.setProposedExpert(expert);
        ticket.setPricingType(TicketPricingType.PLAN_INCLUDED);
        ticket.setQuoteStatus(TicketQuoteStatus.DRAFT);
        when(ticketRepository.findByIdAndDeletedFalse("ticket-1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(expert));
        when(quotaService.getCurrentUsage(ticket.getCreatedBy())).thenReturn(
                SubscriptionQuotaUsageSummaryResponse.builder()
                        .periodStart(LocalDateTime.of(2026, 7, 1, 0, 0))
                        .periodEnd(LocalDateTime.of(2026, 8, 1, 0, 0))
                        .expertTicketsLimit(1)
                        .build());
        when(creditRepository.countByUser_IdAndQuotaCycleAndStatusIn(
                eq(ticket.getCreatedBy().getId()), anyString(), anyList())).thenReturn(1L);

        service.assess(2L, "ticket-1", ExpertTicketAssessmentRequest.builder()
                .decision("ACCEPT").build());

        assertThat(ticket.getStatus()).isEqualTo(LegalTicketStatus.RECLASSIFICATION_REQUESTED);
        assertThat(ticket.getPricingType()).isNull();
        assertThat(ticket.getCustomerPaymentStatus()).isEqualTo(TicketPaymentStatus.UNPAID);
        verify(collaborationService).auditTicket(ticket, expert, "PAID_RECLASSIFICATION_REQUIRED",
                "{\"reason\":\"included-credit-exhausted\"}");
    }

    @Test
    void quoteRejectionReleasesReservedCreditWithoutChargingUser() {
        User customer = user(3L, RoleName.CUSTOMER);
        LegalTicket ticket = ticket(LegalTicketStatus.WAITING_USER_ACCEPTANCE);
        ticket.setCreatedBy(customer);
        ticket.setPricingType(TicketPricingType.PLAN_INCLUDED);
        ticket.setQuotaReservationStatus(TicketQuotaReservationStatus.RESERVED);
        ExpertTicketCreditReservation reservation = ExpertTicketCreditReservation.builder()
                .ticket(ticket).user(customer).quotaCycle("cycle").status(TicketQuotaReservationStatus.RESERVED).build();
        when(ticketRepository.findByIdAndDeletedFalse("ticket-1")).thenReturn(Optional.of(ticket));
        when(creditRepository.findByTicket_Id("ticket-1")).thenReturn(Optional.of(reservation));

        service.decideQuote(3L, "ticket-1", TicketQuoteDecisionRequest.builder().decision("REJECT").build());

        assertThat(ticket.getStatus()).isEqualTo(LegalTicketStatus.CANCELLED_BY_USER);
        assertThat(reservation.getStatus()).isEqualTo(TicketQuotaReservationStatus.RELEASED);
        assertThat(ticket.getQuotaReservationStatus()).isEqualTo(TicketQuotaReservationStatus.RELEASED);
    }

    @Test
    void includedAssignmentConsumesExistingReservationAndUsesInternalValueForRevenue() {
        User expert = user(2L, RoleName.EXPERT);
        LegalTicket ticket = ticket(LegalTicketStatus.PENDING_EXPERT_ACCEPTANCE);
        ticket.setProposedExpert(expert);
        ticket.setPricingType(TicketPricingType.PLAN_INCLUDED);
        ticket.setTicketComplexity(TicketComplexity.BASIC);
        ticket.setCustomerPaymentStatus(TicketPaymentStatus.NOT_REQUIRED);
        ticket.setAcceptanceDueAt(LocalDateTime.now().plusHours(1));
        ExpertTicketCreditReservation reservation = ExpertTicketCreditReservation.builder()
                .ticket(ticket).user(ticket.getCreatedBy()).quotaCycle("cycle")
                .status(TicketQuotaReservationStatus.RESERVED).build();
        when(ticketRepository.findByIdAndDeletedFalse("ticket-1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(expert));
        when(creditRepository.findByTicket_Id("ticket-1")).thenReturn(Optional.of(reservation));

        service.decideAssignment(2L, "ticket-1",
                ExpertAssignmentDecisionRequest.builder().decision("ACCEPT").build());

        assertThat(ticket.getStatus()).isEqualTo(LegalTicketStatus.ASSIGNED_TO_EXPERT);
        assertThat(ticket.getAssignedLawyer()).isSameAs(expert);
        assertThat(reservation.getStatus()).isEqualTo(TicketQuotaReservationStatus.CONSUMED);
        verify(revenueService).applyCommissionSnapshot(ticket);
    }

    @Test
    void paymentConfirmationIsIdempotent() {
        User admin = user(1L, RoleName.ADMIN);
        LegalTicket ticket = ticket(LegalTicketStatus.WAITING_PAYMENT);
        ticket.setPricingType(TicketPricingType.PAID);
        ticket.setQuoteStatus(TicketQuoteStatus.ACCEPTED);
        ticket.setCustomerPaymentStatus(TicketPaymentStatus.UNPAID);
        when(ticketRepository.findByIdAndDeletedFalse("ticket-1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        TicketPaymentConfirmationRequest request = TicketPaymentConfirmationRequest.builder()
                .paymentReference("payment-123").build();
        service.confirmPayment(1L, "ticket-1", request);
        service.confirmPayment(1L, "ticket-1", request);

        assertThat(ticket.getCustomerPaymentStatus()).isEqualTo(TicketPaymentStatus.PAID);
        assertThat(ticket.getStatus()).isEqualTo(LegalTicketStatus.READY_FOR_ASSIGNMENT);
        verify(collaborationService, times(1)).auditTicket(ticket, admin, "PAYMENT_CONFIRMED",
                "{\"reason\":\"payment-123\"}");
    }

    private LegalTicket ticket(LegalTicketStatus status) {
        return LegalTicket.builder().id("ticket-1").ticketType(LegalTicketType.CONTACT_EXPERT)
                .status(status).createdBy(user(3L, RoleName.CUSTOMER)).build();
    }

    private User user(Long id, RoleName role) {
        return User.builder().id(id).active(true).firstName(role.name()).lastName("User")
                .email(role.name().toLowerCase() + "@test.local").role(Role.builder().name(role).build()).build();
    }
}
