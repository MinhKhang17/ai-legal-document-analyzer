package com.analyzer.api.service.impl;

import com.analyzer.api.dto.legalticket.CreateLegalTicketRequest;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.service.LegalTicketService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

/**
 * Temporary ticket service stub.
 * TODO: replace with a repository-backed implementation once the ticket entity exists.
 */
@Service
public class LegalTicketServiceImpl implements LegalTicketService {

    @Override
    public LegalTicketResponse createTicket(CreateLegalTicketRequest request) {
        // TODO: persist ticket metadata once the database model is ready.
        LocalDateTime now = LocalDateTime.now();
        return LegalTicketResponse.builder()
                .id("ticket_" + UUID.randomUUID().toString().replace("-", ""))
                .requestId(request.getRequestId())
                .workspaceId(request.getWorkspaceId())
                .documentId(request.getDocumentId())
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .confidenceScore(request.getConfidenceScore())
                .shouldSuggestTicket(request.getShouldSuggestTicket())
                .suggestionType(request.getSuggestionType())
                .suggestionReason(request.getSuggestionReason())
                .missingInformation(request.getMissingInformation())
                .riskLevel(request.getRiskLevel())
                .legalDomain(request.getLegalDomain())
                .userActionHint(request.getUserActionHint())
                .status(LegalTicketStatus.PENDING_ADMIN_REVIEW)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Override
    public LegalTicketResponse getTicketById(String ticketId) {
        // TODO: look up ticket from persistence when repository support is available.
        LocalDateTime now = LocalDateTime.now();
        return LegalTicketResponse.builder()
                .id(ticketId)
                .status(LegalTicketStatus.PENDING_ADMIN_REVIEW)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Override
    public java.util.List<LegalTicketResponse> listAdminTickets() {
        // TODO: fetch and paginate persisted tickets for the admin console.
        return Collections.emptyList();
    }
}
