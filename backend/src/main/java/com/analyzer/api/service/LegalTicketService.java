package com.analyzer.api.service;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketType;
import com.analyzer.api.enums.RiskLevel;

import java.util.List;

/**
 * Service interface for managing Legal Tickets.
 */
public interface LegalTicketService {

    LegalTicketResponse createTicket(Long customerId, CreateLegalTicketRequest request);

    LegalTicketResponse getTicketById(Long userId, String userRole, String ticketId);

    PageResponse<LegalTicketResponse> getMyTickets(Long customerId, LegalTicketStatus status, int page, int size);

    LegalTicketResponse cancelTicket(Long customerId, String ticketId, CancelLegalTicketRequest request);

    LegalTicketResponse closeTicket(Long customerId, String ticketId, CloseLegalTicketRequest request);

    LegalTicketResponse reopenTicket(Long customerId, String ticketId, ReopenLegalTicketRequest request);

    LegalTicketResponse customerReply(Long customerId, String ticketId, CustomerTicketReplyRequest request);

    PageResponse<LegalTicketResponse> listAdminTickets(LegalTicketStatus status, RiskLevel riskLevel, LegalTicketType ticketType, int page, int size);

    LegalTicketResponse rejectTicket(Long adminId, String ticketId, RejectLegalTicketRequest request);

    List<LegalTicketMessageResponse> getMessages(Long userId, String userRole, String ticketId);
}
