package com.analyzer.api.service.admin.impl;

import com.analyzer.api.dto.legalticket.AssignLawyerRequest;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.Role;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketType;
import com.analyzer.api.enums.ExpertPaymentStatus;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.LegalTicketMessageRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AdminTicketManagementServiceImplTest {

    @Mock LegalTicketRepository ticketRepository;
    @Mock LegalTicketMessageRepository messageRepository;
    @Mock DocumentRepository documentRepository;
    @Mock UserRepository userRepository;
    @Mock LegalTicketMapper mapper;
    @Mock EmailService emailService;

    private AdminTicketManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminTicketManagementServiceImpl(ticketRepository, messageRepository, documentRepository,
                userRepository, mapper, emailService);
    }

    @Test
    void adminAssignsExpertAndMovesPendingTicketToAssigned() {
        User customer = User.builder().id(10L).email("customer@example.com").firstName("Customer").build();
        User expert = activeUser(20L, RoleName.EXPERT);
        LegalTicket ticket = LegalTicket.builder().id("ticket_1").createdBy(customer)
                .ticketType(LegalTicketType.CONTACT_EXPERT).status(LegalTicketStatus.PENDING_ADMIN_REVIEW).build();
        AssignLawyerRequest request = new AssignLawyerRequest();
        request.setLawyerId(20L);

        when(ticketRepository.findById("ticket_1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(20L)).thenReturn(Optional.of(expert));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        when(mapper.toResponse(ticket)).thenReturn(new LegalTicketResponse());

        service.assignLawyer("ticket_1", 1L, request);

        assertThat(ticket.getAssignedLawyer()).isSameAs(expert);
        assertThat(ticket.getStatus()).isEqualTo(LegalTicketStatus.ASSIGNED_TO_LAWYER);
        assertThat(ticket.getAssignedAt()).isNotNull();
        verify(ticketRepository).save(ticket);
    }

    @Test
    void refundTicketCannotBeAssignedToExpert() {
        LegalTicket ticket = LegalTicket.builder().id("ticket_refund")
                .ticketType(LegalTicketType.REFUND_REQUEST).status(LegalTicketStatus.PENDING_ADMIN_REVIEW).build();
        when(ticketRepository.findById("ticket_refund")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.assignLawyer("ticket_refund", 1L, new AssignLawyerRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("REFUND_TICKET_ADMIN_ONLY");
    }

    @Test
    void nonExpertUserCannotBeAssigned() {
        LegalTicket ticket = LegalTicket.builder().id("ticket_customer")
                .ticketType(LegalTicketType.CONTACT_EXPERT).status(LegalTicketStatus.PENDING_ADMIN_REVIEW).build();
        AssignLawyerRequest request = new AssignLawyerRequest();
        request.setLawyerId(30L);
        when(ticketRepository.findById("ticket_customer")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(30L)).thenReturn(Optional.of(activeUser(30L, RoleName.CUSTOMER)));

        assertThatThrownBy(() -> service.assignLawyer("ticket_customer", 1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("ASSIGNEE_MUST_BE_ACTIVE_EXPERT");
    }

    @Test
    void closedTicketCannotBeAssigned() {
        LegalTicket ticket = LegalTicket.builder().id("ticket_closed")
                .ticketType(LegalTicketType.CONTACT_EXPERT).status(LegalTicketStatus.CLOSED).build();
        AssignLawyerRequest request = new AssignLawyerRequest();
        request.setLawyerId(20L);
        when(ticketRepository.findById("ticket_closed")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.assignLawyer("ticket_closed", 1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("INVALID_STATUS_TRANSITION");
    }

    @Test
    void reassigningSameExpertIsNoOp() {
        User expert = activeUser(20L, RoleName.EXPERT);
        LegalTicket ticket = LegalTicket.builder().id("ticket_same").assignedLawyer(expert)
                .ticketType(LegalTicketType.CONTACT_EXPERT).status(LegalTicketStatus.IN_REVIEW).build();
        AssignLawyerRequest request = new AssignLawyerRequest();
        request.setLawyerId(20L);
        when(ticketRepository.findById("ticket_same")).thenReturn(Optional.of(ticket));
        when(mapper.toResponse(ticket)).thenReturn(new LegalTicketResponse());

        service.reassignLawyer("ticket_same", 1L, request);

        verify(ticketRepository, never()).save(ticket);
        verifyNoInteractions(emailService);
    }

    @Test
    void reassigningSameExpertRemainsNoOpWhenPaymentExists() {
        User expert = activeUser(20L, RoleName.EXPERT);
        LegalTicket ticket = LegalTicket.builder().id("ticket_same_paid").assignedLawyer(expert)
                .ticketType(LegalTicketType.CONTACT_EXPERT).status(LegalTicketStatus.IN_REVIEW)
                .expertPaymentStatus(ExpertPaymentStatus.PENDING).build();
        AssignLawyerRequest request = AssignLawyerRequest.builder().lawyerId(20L).build();
        when(ticketRepository.findById("ticket_same_paid")).thenReturn(Optional.of(ticket));
        when(mapper.toResponse(ticket)).thenReturn(new LegalTicketResponse());

        service.reassignLawyer("ticket_same_paid", 1L, request);

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void reassigningDifferentExpertIsBlockedAfterPaymentDataExists() {
        User oldExpert = activeUser(20L, RoleName.EXPERT);
        LegalTicket ticket = LegalTicket.builder().id("ticket_paid").assignedLawyer(oldExpert)
                .ticketType(LegalTicketType.CONTACT_EXPERT).status(LegalTicketStatus.IN_REVIEW)
                .consultationFee(new BigDecimal("500000")).build();
        AssignLawyerRequest request = AssignLawyerRequest.builder().lawyerId(21L).build();
        when(ticketRepository.findById("ticket_paid")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.reassignLawyer("ticket_paid", 1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CANNOT_REASSIGN_TICKET_WITH_PAYMENT_SET");
        verify(userRepository, never()).findById(21L);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void closeInternalRejectsAssignedTicketBeforeExpertResolvesIt() {
        User expert = activeUser(20L, RoleName.EXPERT);
        User admin = User.builder().id(1L).build();
        LegalTicket ticket = LegalTicket.builder().id("ticket_review").assignedLawyer(expert)
                .status(LegalTicketStatus.IN_REVIEW).build();
        when(ticketRepository.findByIdAndDeletedFalse("ticket_review")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.closeInternal("ticket_review", 1L, "note"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("TICKET_NOT_RESOLVED_BY_EXPERT");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void closeInternalAllowsResolvedExpertTicket() {
        User expert = activeUser(20L, RoleName.EXPERT);
        User customer = User.builder().id(10L).email("customer@example.com").firstName("Customer").build();
        User admin = User.builder().id(1L).build();
        LegalTicket ticket = LegalTicket.builder().id("ticket_resolved").assignedLawyer(expert)
                .createdBy(customer).status(LegalTicketStatus.RESOLVED).build();
        when(ticketRepository.findByIdAndDeletedFalse("ticket_resolved")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        when(mapper.toResponse(ticket)).thenReturn(new LegalTicketResponse());

        service.closeInternal("ticket_resolved", 1L, "done");

        verify(ticketRepository).save(ticket);
    }

    private User activeUser(Long id, RoleName roleName) {
        return User.builder()
                .id(id)
                .email(roleName.name().toLowerCase() + "@example.com")
                .firstName(roleName.name())
                .lastName("User")
                .active(true)
                .role(Role.builder().name(roleName).build())
                .build();
    }
}
