package com.analyzer.api.dto.ai;

import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI response contract shared by BE, FE, and the Python service.
 * Metadata is intentionally lightweight so the UI can decide whether to suggest a lawyer ticket.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response payload from the legal AI query flow")
public class AiLegalQueryResponse {

    @JsonProperty("request_id")
    @Schema(description = "Correlation id for the AI call")
    private String requestId;

    @JsonProperty("success")
    @Schema(description = "Whether the AI request completed successfully")
    private Boolean success;

    @JsonProperty("answer")
    @Schema(description = "Natural-language legal answer")
    private String answer;

    @JsonProperty("confidence_score")
    @Schema(description = "Model confidence score in the answer, 0.0 to 1.0")
    private Double confidenceScore;

    @JsonProperty("should_suggest_ticket")
    @Schema(description = "True when the UI should surface ticket creation")
    private Boolean shouldSuggestTicket;

    @JsonProperty("suggestion_type")
    @Schema(description = "What type of follow-up action the AI recommends")
    private SuggestionType suggestionType;

    @JsonProperty("suggestion_reason")
    @Schema(description = "Short explanation for the suggestion")
    private String suggestionReason;

    @JsonProperty("missing_information")
    @Schema(description = "Short summary of facts the AI still needs")
    private String missingInformation;

    @JsonProperty("risk_level")
    @Schema(description = "High-level legal risk classification")
    private RiskLevel riskLevel;

    @JsonProperty("legal_domain")
    @Schema(description = "Detected legal domain, for example tenancy or labor")
    private String legalDomain;

    @JsonProperty("user_action_hint")
    @Schema(description = "Frontend hint for the next user action")
    private UserActionHint userActionHint;

    @JsonProperty("usage")
    @Schema(description = "Token usage information from the model call")
    private Usage usage;

    @JsonProperty("model")
    @Schema(description = "Model name that produced the answer")
    private String model;

    @JsonProperty("error_message")
    @Schema(description = "Human-readable error message when the request fails")
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
