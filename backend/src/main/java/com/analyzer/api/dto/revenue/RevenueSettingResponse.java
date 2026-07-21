package com.analyzer.api.dto.revenue;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RevenueSettingResponse {
    private BigDecimal commissionRate;
    private LocalDateTime updatedAt;
    private String updatedByName;
}
