package com.analyzer.api.dto.revenue;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateRevenueSettingRequest {
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("1.00")
    private BigDecimal commissionRate;
}
