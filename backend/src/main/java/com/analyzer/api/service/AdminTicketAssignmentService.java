package com.analyzer.api.service;

import com.analyzer.api.dto.legalticket.LegalTicketResponse;

/**
 * Contract for admin-side ticket assignment actions.
 */
public interface AdminTicketAssignmentService {

    /**
     * Assign a lawyer to an existing ticket.
     */
    LegalTicketResponse assignLawyer(String ticketId, String lawyerId);
}
