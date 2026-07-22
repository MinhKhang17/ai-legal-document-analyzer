package com.analyzer.api.service.lawyer;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.dto.legalticket.RequestMoreInfoRequest;
import com.analyzer.api.dto.legalticket.ResolveLegalTicketRequest;
import com.analyzer.api.enums.LegalTicketStatus;

/**
 * Service interface for expert lawyer actions on Legal Tickets.
 */
public interface ExpertLegalTicketService {

    PageResponse<LegalTicketResponse> getAssignedTickets(Long expertId, LegalTicketStatus status, int page, int size);

    PageResponse<LegalTicketResponse> getProposedTickets(Long expertId, int page, int size);

    LegalTicketResponse startReview(Long expertId, String ticketId);

    LegalTicketResponse requestMoreInfo(Long expertId, String ticketId, RequestMoreInfoRequest request);

    LegalTicketResponse resolveTicket(Long expertId, String ticketId, ResolveLegalTicketRequest request);
}
