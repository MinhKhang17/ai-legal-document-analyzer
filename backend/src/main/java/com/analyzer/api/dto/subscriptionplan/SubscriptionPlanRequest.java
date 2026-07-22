package com.analyzer.api.dto.subscriptionplan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "Request payload for creating/updating a subscription plan")
public class SubscriptionPlanRequest {

    @Schema(description = "Name of the subscription plan", example = "Premium Plan")
    private String planName;

    @Schema(description = "Type of the subscription plan", example = "PREMIUM")
    private String planType;

    @Schema(description = "Stable unique plan name", example = "PREMIUM")
    private String name;

    @Schema(description = "Customer-facing plan name", example = "Premium")
    private String displayName;

    @Schema(description = "Description of the plan features", example = "Unlimited legal analysis and chat")
    private String description;

    @PositiveOrZero
    @Schema(description = "Price of the plan", example = "299000")
    private BigDecimal price;

    @Positive
    @Schema(description = "Duration of the plan in days", example = "30")
    private Integer durationDays;

    @PositiveOrZero
    @Schema(description = "Maximum contract analysis quota per month", example = "200")
    private Integer maxQuota;

    @Schema(description = "Monthly AI token quota", example = "8500000")
    private Integer aiQuota;

    @Schema(description = "Monthly expert ticket quota", example = "1")
    private Integer ticketQuota;

    @Schema(description = "Maximum workspaces for the plan", example = "20")
    private Integer maxWorkspaces;

    @Schema(description = "Maximum contracts per workspace", example = "50")
    private Integer maxContractsPerWorkspace;

    @Schema(description = "Maximum draft contracts per month", example = "40")
    private Integer maxDraftContracts;

    @Schema(description = "Status of the plan", example = "true")
    private Boolean active;

    @PositiveOrZero private BigDecimal priceVnd;
    @Positive private Integer billingCycleDays;
    @PositiveOrZero private Integer contractAnalysisLimit;
    @PositiveOrZero private Integer aiTokenLimit;
    @PositiveOrZero private Integer workspaceLimit;
    @PositiveOrZero private Integer documentPerWorkspaceLimit;
    @PositiveOrZero private Integer storageLimitMb;
    @PositiveOrZero private Integer maxFileSizeMb;
    @PositiveOrZero private Integer maxAttachedDocumentsPerSession;
    @PositiveOrZero private Integer contractDraftLimit;
    @PositiveOrZero private Integer expertTicketLimit;
    private Boolean allowSystemErrorTicket;
    private Boolean allowQueryErrorTicket;
    private Boolean allowContactExpertTicket;
    private List<String> features;
}
