package com.analyzer.api.service;

import com.analyzer.api.dto.legalticket.CreateLegalTicketRequest;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;

import java.util.List;

/**
 * Contract for customer-facing legal tickets.
 * Persistence is intentionally deferred until the repository/model is ready.
 */
public interface LegalTicketService {

    /**
     * Create a new lawyer ticket from AI metadata.
     */
    LegalTicketResponse createTicket(CreateLegalTicketRequest request);

    /**
     * Load a ticket for the current user or admin.
     */
    LegalTicketResponse getTicketById(String ticketId);

    /**
     * List tickets for the admin console.
     */
    List<LegalTicketResponse> listAdminTickets();
}
