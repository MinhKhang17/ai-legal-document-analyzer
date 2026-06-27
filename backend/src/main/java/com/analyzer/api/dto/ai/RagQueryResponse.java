package com.analyzer.api.dto.ai;

import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RagQueryResponse {
    @JsonProperty("request_id")
    private String requestId;
    @JsonProperty("success")
    private Boolean success;
    @JsonProperty("answer")
    private String answer;
    @JsonProperty("confidence_score")
    private Double confidenceScore;
    @JsonProperty("should_suggest_ticket")
    private Boolean shouldSuggestTicket;
    @JsonProperty("suggestion_type")
    private SuggestionType suggestionType;
    @JsonProperty("suggestion_reason")
    private String suggestionReason;
    @JsonProperty("missing_information")
    private String missingInformation;
    @JsonProperty("risk_level")
    private RiskLevel riskLevel;
    @JsonProperty("legal_domain")
    private String legalDomain;
    @JsonProperty("user_action_hint")
    private UserActionHint userActionHint;
    @JsonProperty("usage")
    private Usage usage;
    @JsonProperty("model")
    private String model;
    @JsonProperty("error_message")
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
