package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSummaryResponse {

    private String ticketId;
    private Double confidenceScore;
    private RiskLevel riskLevel;
    private SuggestionType suggestionType;
    private String suggestionReason;
    private String summary;
}
