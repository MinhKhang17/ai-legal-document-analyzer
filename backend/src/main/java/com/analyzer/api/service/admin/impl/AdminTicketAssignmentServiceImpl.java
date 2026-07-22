package com.analyzer.api.service.admin.impl;

import com.analyzer.api.dto.legalticket.AssignLawyerRequest;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.LegalTicketMessage;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.LegalTicketMessageType;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketType;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.legalticket.LegalTicketMessageRepository;
import com.analyzer.api.repository.legalticket.LegalTicketRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.service.admin.AdminTicketAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminTicketAssignmentServiceImpl implements AdminTicketAssignmentService {

    private final LegalTicketRepository legalTicketRepository;
    private final UserRepository userRepository;
    private final LegalTicketMessageRepository legalTicketMessageRepository;
    private final LegalTicketMapper legalTicketMapper;

    @Override
    @Transactional
    public LegalTicketResponse assignLawyer(Long adminId, String ticketId, AssignLawyerRequest request) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        if (ticket.getTicketType() == LegalTicketType.REFUND_REQUEST) throw new ConflictException("REFUND_TICKET_ADMIN_ONLY");
        if (ticket.getStatus() != LegalTicketStatus.PENDING_ADMIN_REVIEW &&
            ticket.getStatus() != LegalTicketStatus.REOPENED &&
            ticket.getStatus() != LegalTicketStatus.ASSIGNED_TO_LAWYER) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        if (ticket.getStatus() == LegalTicketStatus.ASSIGNED_TO_LAWYER &&
            ticket.getAssignedLawyer() != null &&
            !Boolean.TRUE.equals(request.getForceReassign())) {
            throw new ConflictException("TICKET_ALREADY_ASSIGNED");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("ADMIN_NOT_FOUND"));

        User lawyer = userRepository.findById(request.getLawyerId())
                .orElseThrow(() -> new ResourceNotFoundException("LAWYER_NOT_FOUND"));

        if (lawyer.getRole() == null || lawyer.getRole().getName() != RoleName.EXPERT) {
            throw new RuntimeException("USER_IS_NOT_EXPERT");
        }

        if (!lawyer.isActive()) {
            throw new RuntimeException("EXPERT_NOT_ACTIVE");
        }

        ticket.setAssignedLawyer(lawyer);
        ticket.setStatus(LegalTicketStatus.ASSIGNED_TO_LAWYER);
        ticket.setAssignedAt(LocalDateTime.now());
        ticket.setAdminNote(request.getAdminNote());

        // Create System Message
        LegalTicketMessage systemMsg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(admin)
                .content("Ticket được gán cho chuyên gia: " + lawyer.getFirstName() + " " + lawyer.getLastName())
                .messageType(LegalTicketMessageType.SYSTEM)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(systemMsg);

        // Save admin internal note if present
        if (request.getAdminNote() != null && !request.getAdminNote().isBlank()) {
            LegalTicketMessage noteMsg = LegalTicketMessage.builder()
                    .ticket(ticket)
                    .sender(admin)
                    .content(request.getAdminNote())
                    .messageType(LegalTicketMessageType.ADMIN_NOTE)
                    .internalOnly(true)
                    .build();
            legalTicketMessageRepository.save(noteMsg);
        }

        return legalTicketMapper.toResponse(ticket);
    }

    @Override
    @Transactional
    public LegalTicketResponse reassignLawyer(Long adminId, String ticketId, AssignLawyerRequest request) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));

        if (ticket.getTicketType() == LegalTicketType.REFUND_REQUEST) throw new ConflictException("REFUND_TICKET_ADMIN_ONLY");
        if (ticket.getStatus() == LegalTicketStatus.CLOSED || ticket.getStatus() == LegalTicketStatus.CANCELLED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        if (ticket.getStatus() == LegalTicketStatus.RESOLVED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("ADMIN_NOT_FOUND"));

        User lawyer = userRepository.findById(request.getLawyerId())
                .orElseThrow(() -> new ResourceNotFoundException("LAWYER_NOT_FOUND"));

        if (lawyer.getRole() == null || lawyer.getRole().getName() != RoleName.EXPERT) {
            throw new RuntimeException("USER_IS_NOT_EXPERT");
        }

        if (!lawyer.isActive()) {
            throw new RuntimeException("EXPERT_NOT_ACTIVE");
        }

        ticket.setAssignedLawyer(lawyer);
        ticket.setStatus(LegalTicketStatus.ASSIGNED_TO_LAWYER);
        ticket.setAssignedAt(LocalDateTime.now());
        ticket.setAdminNote(request.getAdminNote());

        // Create System Message
        LegalTicketMessage systemMsg = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(admin)
                .content("Ticket được chuyển cho chuyên gia mới: " + lawyer.getFirstName() + " " + lawyer.getLastName())
                .messageType(LegalTicketMessageType.SYSTEM)
                .internalOnly(false)
                .build();
        legalTicketMessageRepository.save(systemMsg);

        // Save admin internal note if present
        if (request.getAdminNote() != null && !request.getAdminNote().isBlank()) {
            LegalTicketMessage noteMsg = LegalTicketMessage.builder()
                    .ticket(ticket)
                    .sender(admin)
                    .content(request.getAdminNote())
                    .messageType(LegalTicketMessageType.ADMIN_NOTE)
                    .internalOnly(true)
                    .build();
            legalTicketMessageRepository.save(noteMsg);
        }

        return legalTicketMapper.toResponse(ticket);
    }
}
