package com.analyzer.api.service.impl;

import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.service.AdminTicketAssignmentService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Temporary admin ticket assignment stub.
 * TODO: connect this to a real ticket repository and audit trail.
 */
@Service
public class AdminTicketAssignmentServiceImpl implements AdminTicketAssignmentService {

    @Override
    public LegalTicketResponse assignLawyer(String ticketId, String lawyerId) {
        // TODO: update the ticket record and persist the assignment state.
        LocalDateTime now = LocalDateTime.now();
        return LegalTicketResponse.builder()
                .id(ticketId)
                .assignedLawyerId(lawyerId)
                .status(LegalTicketStatus.ASSIGNED_TO_LAWYER)
                .updatedAt(now)
                .build();
    }
}
