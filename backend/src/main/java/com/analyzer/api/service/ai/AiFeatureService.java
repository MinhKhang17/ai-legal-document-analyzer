package com.analyzer.api.service.ai;

import com.analyzer.api.dto.ai.AiFeatureSummaryResponse;
import com.analyzer.api.dto.ai.AiRiskAssessmentResponse;
import com.analyzer.api.dto.ai.AiSuggestionResponse;

import java.util.List;

public interface AiFeatureService {

    AiRiskAssessmentResponse getTicketAssessment(String ticketId);

    AiFeatureSummaryResponse getTicketSummary(String ticketId);

    List<AiSuggestionResponse> getTicketSuggestions(String ticketId);
}
