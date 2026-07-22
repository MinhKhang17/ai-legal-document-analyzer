package com.analyzer.api.service.ai.impl;

import com.analyzer.api.dto.ai.AiFeatureSummaryResponse;
import com.analyzer.api.dto.ai.AiRiskAssessmentResponse;
import com.analyzer.api.dto.ai.AiSuggestionResponse;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.legalticket.LegalTicketRepository;
import com.analyzer.api.service.ai.AiFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiFeatureServiceImpl implements AiFeatureService {

    private final LegalTicketRepository legalTicketRepository;

    @Override
    @Transactional(readOnly = true)
    public AiRiskAssessmentResponse getTicketAssessment(String ticketId) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        return AiRiskAssessmentResponse.builder()
                .requestId("req_" + ticket.getId())
                .confidenceScore(ticket.getConfidenceScore() != null ? ticket.getConfidenceScore() : 0.88)
                .riskLevel(ticket.getRiskLevel() != null ? ticket.getRiskLevel() : RiskLevel.MEDIUM)
                .shouldSuggestLawyer(true)
                .suggestionType(ticket.getSuggestionType() != null ? ticket.getSuggestionType() : SuggestionType.SUGGEST_LAWYER)
                .suggestionReason(ticket.getSuggestionReason() != null ? ticket.getSuggestionReason() : "Tài liệu có các điều khoản mơ hồ về bồi thường thiệt hại.")
                .missingInformation("Chưa đính kèm phụ lục hợp đồng.")
                .userActionHint(UserActionHint.CREATE_TICKET)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AiFeatureSummaryResponse getTicketSummary(String ticketId) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        return AiFeatureSummaryResponse.builder()
                .requestId("req_" + ticket.getId())
                .confidenceScore(ticket.getConfidenceScore() != null ? ticket.getConfidenceScore() : 0.88)
                .riskLevel(ticket.getRiskLevel() != null ? ticket.getRiskLevel() : RiskLevel.MEDIUM)
                .summary(ticket.getCustomerNote() != null ? ticket.getCustomerNote() : ticket.getQuestion())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiSuggestionResponse> getTicketSuggestions(String ticketId) {
        legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        return Collections.emptyList();
    }
}
