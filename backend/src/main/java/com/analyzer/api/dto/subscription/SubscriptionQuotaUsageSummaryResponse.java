package com.analyzer.api.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionQuotaUsageSummaryResponse {

    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Long aiTokensUsed;
    private Integer aiTokensLimit;
    private Integer expertTicketsUsed;
    private Integer expertTicketsLimit;
    private Long storageUsedBytes;
    private Long storageLimitBytes;
}
