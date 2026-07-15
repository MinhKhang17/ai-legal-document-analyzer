package com.analyzer.api.dto.subscription;

import com.analyzer.api.enums.UsageEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionUsageResponse {

    private Long id;
    private Long customerPlanId;
    private UsageEventType usageType;
    private String referenceId;
    private Integer consumedUnits;
    private String metadataJson;
    private LocalDateTime createdAt;
}
