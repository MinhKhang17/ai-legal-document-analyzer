package com.analyzer.api.dto.subscriptionplan;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Response payload containing subscription plan details")
public class SubscriptionPlanResponseDTO {

    @Schema(description = "Plan ID", example = "1")
    private Long id;

    @Schema(description = "Name of the subscription plan", example = "Premium Plan")
    private String planName;

    @Schema(description = "Type of the subscription plan", example = "PREMIUM")
    private String planType;

    @Schema(description = "Description of the plan features", example = "Unlimited legal analysis and chat")
    private String description;

    @Schema(description = "Price of the plan", example = "299000")
    private BigDecimal price;

    @Schema(description = "Duration of the plan in days", example = "30")
    private Integer durationDays;

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

    @Schema(description = "Creation date time")
    private LocalDateTime createdAt;

    @Schema(description = "Last update date time")
    private LocalDateTime updatedAt;

    private String name;
    private String displayName;
    private BigDecimal priceVnd;
    private Integer billingCycleDays;
    private Integer contractAnalysisLimit;
    private Integer aiTokenLimit;
    private Integer workspaceLimit;
    private Integer documentPerWorkspaceLimit;
    private Integer storageLimitMb;
    private Integer maxFileSizeMb;
    private Integer maxAttachedDocumentsPerSession;
    private Integer contractDraftLimit;
    private Integer expertTicketLimit;
    private Boolean allowSystemErrorTicket;
    private Boolean allowQueryErrorTicket;
    private Boolean allowContactExpertTicket;
    private List<String> features;
}
