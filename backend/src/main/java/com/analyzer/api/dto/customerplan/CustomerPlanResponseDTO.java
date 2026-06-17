package com.analyzer.api.dto.customerplan;

import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanResponseDTO;
import com.analyzer.api.enums.PlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "Response payload containing customer plan details")
public class CustomerPlanResponseDTO {

    @Schema(description = "Customer Plan ID", example = "1")
    private Long id;

    @Schema(description = "Customer ID", example = "2")
    private Long customerId;

    @Schema(description = "Subscription Plan Details")
    private SubscriptionPlanResponseDTO subscriptionPlan;

    @Schema(description = "Plan Status", example = "ACTIVE")
    private PlanStatus status;

    @Schema(description = "Plan Start Date")
    private LocalDateTime startDate;

    @Schema(description = "Plan End Date")
    private LocalDateTime endDate;

    @Schema(description = "Used general quota", example = "5")
    private Integer usedQuota;

    @Schema(description = "Auto renew subscription status", example = "false")
    private Boolean autoRenew;

    @Schema(description = "Remaining quota", example = "95")
    private Integer remainingQuota;

    @Schema(description = "Creation date time")
    private LocalDateTime createdAt;

    @Schema(description = "Last update date time")
    private LocalDateTime updatedAt;
}
