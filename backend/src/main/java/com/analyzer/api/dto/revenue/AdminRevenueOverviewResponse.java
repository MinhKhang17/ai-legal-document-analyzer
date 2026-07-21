package com.analyzer.api.dto.revenue;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AdminRevenueOverviewResponse {
    private long totalTicketCount;
    private long paidTicketCount;
    private long pendingPaymentTicketCount;
    private BigDecimal totalConsultationFee;
    private BigDecimal totalPlatformFee;
    private BigDecimal totalExpertPayout;
    private List<ExpertRevenueBreakdownItem> byExpert;
}
