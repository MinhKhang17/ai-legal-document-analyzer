package com.analyzer.api.dto.ai;

import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRiskAssessmentResponse {

    private String requestId;
    private Double confidenceScore;
    private RiskLevel riskLevel;
    private Boolean shouldSuggestLawyer;
    private SuggestionType suggestionType;
    private String suggestionReason;
    private String missingInformation;
    private UserActionHint userActionHint;
}
