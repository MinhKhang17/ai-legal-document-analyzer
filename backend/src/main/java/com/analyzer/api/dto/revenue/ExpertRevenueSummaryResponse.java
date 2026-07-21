package com.analyzer.api.dto.revenue;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ExpertRevenueSummaryResponse {
    private long assignedTicketCount;
    private long resolvedTicketCount;
    private long paidTicketCount;
    private long pendingPaymentTicketCount;
    private BigDecimal totalRevenue;
    private BigDecimal paidRevenue;
    private BigDecimal pendingRevenue;
    private BigDecimal totalPlatformFee;
    private BigDecimal totalExpertPayout;
}
