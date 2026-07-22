package com.analyzer.api.service.legalticket;

import com.analyzer.api.dto.legalticket.*;

public interface ExpertTicketWorkflowService {
    LegalTicketResponse classify(Long adminId, String ticketId, AdminTicketClassificationRequest request);

    LegalTicketResponse assess(Long expertId, String ticketId, ExpertTicketAssessmentRequest request);

    LegalTicketResponse decideQuote(Long customerId, String ticketId, TicketQuoteDecisionRequest request);

    LegalTicketResponse confirmPayment(Long adminId, String ticketId, TicketPaymentConfirmationRequest request);

    LegalTicketResponse offerAssignment(Long adminId, String ticketId, Long expertId, String reason);

    LegalTicketResponse reassign(Long adminId, String ticketId, Long expertId, String reason);

    LegalTicketResponse decideAssignment(Long expertId, String ticketId, ExpertAssignmentDecisionRequest request);

    LegalTicketResponse extendSla(Long adminId, String ticketId, AdminSlaExtensionRequest request);
}
