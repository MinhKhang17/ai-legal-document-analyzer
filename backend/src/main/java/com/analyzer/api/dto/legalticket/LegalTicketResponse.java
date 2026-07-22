package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketType;
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
import java.math.BigDecimal;
import java.util.List;
import com.analyzer.api.enums.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Legal ticket response details")
public class LegalTicketResponse {

    private TicketCreationSource creationSource;
    private String legalIssueCategory;
    private String contractType;
    private String userExpectedOutcome;
    private Long userId;
    private String userDisplayName;
    private String userEmail;
    private String userPhone;
    private List<String> selectedDocumentIds;
    private List<String> sharedProfileFields;
    private LocalDateTime consentGrantedAt;
    private LocalDateTime consentRevokedAt;
    private String aiQuestion;
    private String aiAnswerSummary;
    private String aiIntent;
    private RiskLevel aiRiskLevel;
    private Double aiConfidence;
    private TicketComplexity ticketComplexity;
    private String classificationReason;
    private LocalDateTime classifiedAt;
    private Long classifiedById;
    private TicketPricingType pricingType;
    private BigDecimal userPrice;
    private BigDecimal internalTicketValue;
    private TicketQuoteStatus quoteStatus;
    private TicketPaymentStatus paymentStatus;
    private String quotaCycle;
    private TicketQuotaReservationStatus quotaReservationStatus;
    private Long proposedExpertId;
    private String proposedExpertName;
    private LocalDateTime assignmentOfferedAt;
    private LocalDateTime acceptanceDueAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime firstResponseDueAt;
    private LocalDateTime firstRespondedAt;
    private LocalDateTime resolutionDueAt;
    private LocalDateTime completedAt;
    private LocalDateTime lastExpertActivityAt;
    private TicketSlaStatus slaStatus;
    private LocalDateTime pausedAt;
    private Long totalPausedDurationSeconds;
    private String extensionReason;
    private FailureResponsibleParty failureResponsibleParty;
    private Long previousExpertId;
    private String reassignmentReason;
    private LocalDateTime reassignedAt;
    private Long reassignedById;
    private Integer completionPercent;
    private BigDecimal approvedPartialPayout;
    private String contributionNote;

    private String ticketCode;
    private String title;
    private String description;
    private String recipientType;
    private String priority;
    private String conversationScope;
    private String sourceUserMessageId;
    private String sourceAssistantMessageId;
    private String focusedDocumentId;
    private List<String> sharedDocumentIds;
    private TicketContextSnapshotResponse contextSnapshot;

    @JsonProperty("ticket_type")
    private LegalTicketType ticketType;

    @JsonProperty("chat_session_id")
    private String relatedChatSessionId;

    @JsonProperty("chat_message_id")
    private String relatedChatMessageId;

    @JsonProperty("id")
    private String id;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("workspace_id")
    private String workspaceId;

    @JsonProperty("workspace_name")
    private String workspaceName;

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("document_name")
    private String documentName;

    @JsonProperty("created_by_id")
    private Long createdById;

    @JsonProperty("created_by_name")
    private String createdByName;

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
    private Long assignedLawyerId;

    @JsonProperty("assigned_lawyer_name")
    private String assignedLawyerName;

    @JsonProperty("issue_fingerprint")
    private String issueFingerprint;

    @JsonProperty("customer_note")
    private String customerNote;

    @JsonProperty("issue_title")
    private String issueTitle;

    @JsonProperty("issue_summary")
    private String issueSummary;

    @JsonProperty("problematic_clause")
    private String problematicClause;

    @JsonProperty("clause_reference")
    private String clauseReference;

    @JsonProperty("page_number")
    private Integer pageNumber;

    @JsonProperty("ai_evidence")
    private String aiEvidence;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    @JsonProperty("expert_answer")
    private String expertAnswer;

    @JsonProperty("expert_internal_note")
    private String expertInternalNote;

    @JsonProperty("consultation_fee")
    private BigDecimal consultationFee;

    @JsonProperty("commission_rate")
    private BigDecimal commissionRate;

    @JsonProperty("platform_fee")
    private BigDecimal platformFee;

    @JsonProperty("expert_payout")
    private BigDecimal expertPayout;

    @JsonProperty("expert_payment_status")
    private com.analyzer.api.enums.ExpertPaymentStatus expertPaymentStatus;

    @JsonProperty("expert_paid_at")
    private LocalDateTime expertPaidAt;

    @JsonProperty("admin_note")
    private String adminNote;

    @JsonProperty("rejection_reason")
    private String rejectionReason;

    @JsonProperty("assigned_at")
    private LocalDateTime assignedAt;

    @JsonProperty("resolved_at")
    private LocalDateTime resolvedAt;

    @JsonProperty("closed_at")
    private LocalDateTime closedAt;

    @JsonProperty("cancelled_at")
    private LocalDateTime cancelledAt;

    @JsonProperty("reopened_at")
    private LocalDateTime reopenedAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
