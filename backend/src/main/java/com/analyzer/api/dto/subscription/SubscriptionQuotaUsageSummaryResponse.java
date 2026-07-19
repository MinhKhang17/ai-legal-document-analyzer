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
    private Integer contractAnalysisUsed;
    private Integer contractAnalysisLimit;
    private Long aiTokensUsed;
    private Integer aiTokensLimit;
    private Integer draftContractsUsed;
    private Integer draftContractsLimit;
    private Integer expertTicketsUsed;
    private Integer expertTicketsLimit;
    private Integer workspacesUsed;
    private Integer workspacesLimit;
    private Integer documentsUsed;
    private Integer documentsLimit;
    private Long storageUsedBytes;
    private Long storageLimitBytes;
}
