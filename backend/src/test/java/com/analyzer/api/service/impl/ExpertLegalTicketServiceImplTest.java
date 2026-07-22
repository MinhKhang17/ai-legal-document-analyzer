package com.analyzer.api.service.impl;

import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.dto.legalticket.RequestMoreInfoRequest;
import com.analyzer.api.dto.legalticket.ResolveLegalTicketRequest;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.LegalTicketMessageRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.EmailService;
import com.analyzer.api.service.ExpertRevenueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpertLegalTicketServiceImplTest {

    @Mock LegalTicketRepository ticketRepository;
    @Mock LegalTicketMessageRepository messageRepository;
    @Mock UserRepository userRepository;
    @Mock LegalTicketMapper mapper;
    @Mock EmailService emailService;
    @Mock ExpertRevenueService expertRevenueService;
    @Mock RevenuePayrollService revenuePayrollService;

    private ExpertLegalTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExpertLegalTicketServiceImpl(ticketRepository, messageRepository, userRepository, mapper,
                emailService, expertRevenueService, revenuePayrollService);
    }

    @Test
    void assignedExpertCanReviewRequestInfoAndResolveAfterCustomerResponse() {
        LegalTicket ticket = assignedTicket(LegalTicketStatus.ASSIGNED_TO_LAWYER);
        when(ticketRepository.findByIdAndDeletedFalse("ticket_1")).thenReturn(Optional.of(ticket));
        when(mapper.toResponse(ticket)).thenReturn(new LegalTicketResponse());

        service.startReview(20L, "ticket_1");
        assertThat(ticket.getStatus()).isEqualTo(LegalTicketStatus.IN_REVIEW);

        service.requestMoreInfo(20L, "ticket_1", RequestMoreInfoRequest.builder().message("Bo sung ho so").build());
        assertThat(ticket.getStatus()).isEqualTo(LegalTicketStatus.NEED_MORE_INFO);

        ticket.setStatus(LegalTicketStatus.CUSTOMER_RESPONDED);
        service.resolveTicket(20L, "ticket_1", ResolveLegalTicketRequest.builder()
                .expertAnswer("Ket luan chuyen gia").expertInternalNote("Ghi chu noi bo").build());

        assertThat(ticket.getStatus()).isEqualTo(LegalTicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();
        assertThat(ticket.getExpertAnswer()).isEqualTo("Ket luan chuyen gia");
        verify(messageRepository, times(4)).save(any());
    }

    @Test
    void unassignedExpertCannotStartReview() {
        LegalTicket ticket = assignedTicket(LegalTicketStatus.ASSIGNED_TO_LAWYER);
        when(ticketRepository.findByIdAndDeletedFalse("ticket_1")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.startReview(99L, "ticket_1"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("NOT_ASSIGNED_EXPERT");
    }

    @Test
    void expertCannotResolveClosedTicket() {
        LegalTicket ticket = assignedTicket(LegalTicketStatus.CLOSED);
        when(ticketRepository.findByIdAndDeletedFalse("ticket_1")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.resolveTicket(20L, "ticket_1",
                ResolveLegalTicketRequest.builder().expertAnswer("answer").build()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("INVALID_STATUS_TRANSITION");
    }

    private LegalTicket assignedTicket(LegalTicketStatus status) {
        User customer = User.builder().id(10L).email("customer@example.com").firstName("Customer").build();
        User expert = User.builder().id(20L).email("expert@example.com").firstName("Expert").build();
        return LegalTicket.builder().id("ticket_1").createdBy(customer).assignedLawyer(expert).status(status).build();
    }
}
