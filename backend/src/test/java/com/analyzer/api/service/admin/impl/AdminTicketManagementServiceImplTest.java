package com.analyzer.api.service.admin.impl;

import com.analyzer.api.dto.legalticket.AssignLawyerRequest;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.Role;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.ExpertPaymentStatus;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.LegalTicketMessageRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.EmailService;
import com.analyzer.api.service.ExpertRevenueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminTicketManagementServiceImplTest {
    @Mock LegalTicketRepository legalTicketRepository;
    @Mock LegalTicketMessageRepository legalTicketMessageRepository;
    @Mock DocumentRepository documentRepository;
    @Mock UserRepository userRepository;
    @Mock LegalTicketMapper legalTicketMapper;
    @Mock EmailService emailService;
    @Mock ExpertRevenueService expertRevenueService;

    private AdminTicketManagementServiceImpl service() {
        return new AdminTicketManagementServiceImpl(legalTicketRepository, legalTicketMessageRepository,
                documentRepository, userRepository, legalTicketMapper, emailService, expertRevenueService);
    }

    @Test
    void assignLawyerRejectsUserWithoutExpertRole() {
        LegalTicket ticket = LegalTicket.builder().id("t1").status(LegalTicketStatus.PENDING_ADMIN_REVIEW).build();
        User notExpert = User.builder().id(5L).role(Role.builder().name(RoleName.CUSTOMER).build()).build();
        AssignLawyerRequest request = AssignLawyerRequest.builder().lawyerId(5L).build();
        when(legalTicketRepository.findById("t1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(5L)).thenReturn(Optional.of(notExpert));

        assertThrows(ConflictException.class, () -> service().assignLawyer("t1", 1L, request));
        verify(legalTicketRepository, never()).save(any());
    }

    @Test
    void assignLawyerSucceedsForExpertRole() {
        LegalTicket ticket = LegalTicket.builder().id("t1").status(LegalTicketStatus.PENDING_ADMIN_REVIEW)
                .createdBy(User.builder().id(2L).email("customer@test.com").firstName("Cus").build()).build();
        User expert = User.builder().id(5L).email("expert@test.com").firstName("Exp")
                .role(Role.builder().name(RoleName.EXPERT).build()).build();
        AssignLawyerRequest request = AssignLawyerRequest.builder().lawyerId(5L).build();
        when(legalTicketRepository.findById("t1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(5L)).thenReturn(Optional.of(expert));
        when(legalTicketRepository.save(ticket)).thenReturn(ticket);
        when(legalTicketMapper.toResponse(ticket)).thenReturn(LegalTicketResponse.builder().build());

        service().assignLawyer("t1", 1L, request);

        verify(legalTicketRepository).save(ticket);
    }

    @Test
    void closeInternalRejectsTicketWithAssignedExpertThatIsNotResolvedYet() {
        User expert = User.builder().id(5L).role(Role.builder().name(RoleName.EXPERT).build()).build();
        LegalTicket ticket = LegalTicket.builder().id("t1").status(LegalTicketStatus.IN_REVIEW)
                .assignedLawyer(expert).build();
        User admin = User.builder().id(1L).build();
        when(legalTicketRepository.findByIdAndDeletedFalse("t1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(ConflictException.class, () -> service().closeInternal("t1", 1L, "note"));
        verify(expertRevenueService, never()).applyCommissionSnapshot(any());
    }

    @Test
    void closeInternalAllowsClosingUnassignedTicketFromAnyStatus() {
        LegalTicket ticket = LegalTicket.builder().id("t1").status(LegalTicketStatus.PENDING_ADMIN_REVIEW)
                .createdBy(User.builder().id(2L).email("customer@test.com").firstName("Cus").build()).build();
        User admin = User.builder().id(1L).build();
        when(legalTicketRepository.findByIdAndDeletedFalse("t1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(legalTicketRepository.save(ticket)).thenReturn(ticket);
        when(legalTicketMapper.toResponse(ticket)).thenReturn(LegalTicketResponse.builder().build());

        service().closeInternal("t1", 1L, "note");

        verify(expertRevenueService).applyCommissionSnapshot(ticket);
        verify(legalTicketRepository).save(ticket);
    }

    @Test
    void closeInternalAllowsClosingResolvedTicketWithAssignedExpert() {
        User expert = User.builder().id(5L).role(Role.builder().name(RoleName.EXPERT).build()).build();
        LegalTicket ticket = LegalTicket.builder().id("t1").status(LegalTicketStatus.RESOLVED)
                .assignedLawyer(expert)
                .createdBy(User.builder().id(2L).email("customer@test.com").firstName("Cus").build()).build();
        User admin = User.builder().id(1L).build();
        when(legalTicketRepository.findByIdAndDeletedFalse("t1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(legalTicketRepository.save(ticket)).thenReturn(ticket);
        when(legalTicketMapper.toResponse(ticket)).thenReturn(LegalTicketResponse.builder().build());

        service().closeInternal("t1", 1L, "note");

        verify(expertRevenueService).applyCommissionSnapshot(ticket);
        verify(legalTicketRepository).save(ticket);
    }

    @Test
    void reassignLawyerRejectsWhenPaymentStatusAlreadySet() {
        User oldExpert = User.builder().id(5L).role(Role.builder().name(RoleName.EXPERT).build()).build();
        LegalTicket ticket = LegalTicket.builder().id("t1").status(LegalTicketStatus.RESOLVED)
                .assignedLawyer(oldExpert).expertPaymentStatus(ExpertPaymentStatus.PENDING).build();
        AssignLawyerRequest request = AssignLawyerRequest.builder().lawyerId(6L).build();
        when(legalTicketRepository.findById("t1")).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service().reassignLawyer("t1", 1L, request));
        verify(userRepository, never()).findById(6L);
        verify(legalTicketRepository, never()).save(any());
    }

    @Test
    void reassignLawyerRejectsWhenConsultationFeeAlreadySet() {
        User oldExpert = User.builder().id(5L).role(Role.builder().name(RoleName.EXPERT).build()).build();
        LegalTicket ticket = LegalTicket.builder().id("t1").status(LegalTicketStatus.RESOLVED)
                .assignedLawyer(oldExpert).consultationFee(new BigDecimal("500000")).build();
        AssignLawyerRequest request = AssignLawyerRequest.builder().lawyerId(6L).build();
        when(legalTicketRepository.findById("t1")).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service().reassignLawyer("t1", 1L, request));
        verify(legalTicketRepository, never()).save(any());
    }

    @Test
    void reassignLawyerRejectsWhenCommissionRateAlreadySnapshotted() {
        User oldExpert = User.builder().id(5L).role(Role.builder().name(RoleName.EXPERT).build()).build();
        LegalTicket ticket = LegalTicket.builder().id("t1").status(LegalTicketStatus.RESOLVED)
                .assignedLawyer(oldExpert).consultationFee(BigDecimal.ZERO)
                .commissionRate(new BigDecimal("0.2000")).build();
        AssignLawyerRequest request = AssignLawyerRequest.builder().lawyerId(6L).build();
        when(legalTicketRepository.findById("t1")).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service().reassignLawyer("t1", 1L, request));
        verify(legalTicketRepository, never()).save(any());
    }

    @Test
    void reassignLawyerSucceedsWhenNoPaymentDataYet() {
        User oldExpert = User.builder().id(5L).role(Role.builder().name(RoleName.EXPERT).build()).build();
        User newExpert = User.builder().id(6L).email("new@test.com").firstName("New")
                .role(Role.builder().name(RoleName.EXPERT).build()).build();
        LegalTicket ticket = LegalTicket.builder().id("t1").status(LegalTicketStatus.ASSIGNED_TO_LAWYER)
                .assignedLawyer(oldExpert)
                .createdBy(User.builder().id(2L).email("customer@test.com").firstName("Cus").build()).build();
        AssignLawyerRequest request = AssignLawyerRequest.builder().lawyerId(6L).build();
        when(legalTicketRepository.findById("t1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(6L)).thenReturn(Optional.of(newExpert));
        when(legalTicketRepository.save(ticket)).thenReturn(ticket);
        when(legalTicketMapper.toResponse(ticket)).thenReturn(LegalTicketResponse.builder().build());

        service().reassignLawyer("t1", 1L, request);

        verify(legalTicketRepository).save(ticket);
    }
}
