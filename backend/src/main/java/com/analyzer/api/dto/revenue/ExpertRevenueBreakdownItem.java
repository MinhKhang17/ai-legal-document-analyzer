package com.analyzer.api.dto.revenue;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ExpertRevenueBreakdownItem {
    private Long expertId;
    private String expertName;
    private long ticketCount;
    private BigDecimal totalConsultationFee;
    private BigDecimal totalExpertPayout;
}
