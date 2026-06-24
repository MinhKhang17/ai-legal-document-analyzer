package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read model for lawyer tickets exposed to customers and admins.
 * Keep this response stable so the FE can render ticket status without DB knowledge.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Legal ticket response")
public class LegalTicketResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("workspace_id")
    private String workspaceId;

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("question")
    private String question;

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

    @JsonProperty("status")
    private LegalTicketStatus status;

    @JsonProperty("assigned_lawyer_id")
    private String assignedLawyerId;

    @JsonProperty("assigned_lawyer_name")
    private String assignedLawyerName;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
