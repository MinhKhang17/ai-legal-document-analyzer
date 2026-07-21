package com.analyzer.api.service.admin.impl;

import com.analyzer.api.dto.legalticket.AssignLawyerRequest;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketType;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        User expert = User.builder().id(20L).email("expert@example.com").firstName("Expert").build();
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
}
