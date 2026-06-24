package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal request body for creating a lawyer ticket from an AI conversation.
 * The BE can fill most fields from the stored AI response metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a legal ticket")
public class CreateLegalTicketRequest {

    @JsonProperty("request_id")
    @Schema(description = "AI request id that triggered the ticket suggestion")
    private String requestId;

    @JsonProperty("workspace_id")
    @Schema(description = "Workspace identifier associated with the legal issue")
    private String workspaceId;

    @JsonProperty("document_id")
    @Schema(description = "Optional document identifier related to the question")
    private String documentId;

    @JsonProperty("question")
    @Schema(description = "Original user question")
    private String question;

    @JsonProperty("answer")
    @Schema(description = "Latest AI answer shown to the user")
    private String answer;

    @JsonProperty("confidence_score")
    @Schema(description = "AI confidence score copied into the ticket context")
    private Double confidenceScore;

    @JsonProperty("should_suggest_ticket")
    @Schema(description = "Whether the AI explicitly recommended ticket creation")
    private Boolean shouldSuggestTicket;

    @JsonProperty("suggestion_type")
    @Schema(description = "Suggestion category for the ticket")
    private SuggestionType suggestionType;

    @JsonProperty("suggestion_reason")
    @Schema(description = "Reason the AI wants human review")
    private String suggestionReason;

    @JsonProperty("missing_information")
    @Schema(description = "What facts are still missing from the user")
    private String missingInformation;

    @JsonProperty("risk_level")
    @Schema(description = "AI risk level at the time the ticket was created")
    private RiskLevel riskLevel;

    @JsonProperty("legal_domain")
    @Schema(description = "Detected legal domain")
    private String legalDomain;

    @JsonProperty("user_action_hint")
    @Schema(description = "Frontend hint captured with the ticket")
    private UserActionHint userActionHint;
}
