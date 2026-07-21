package com.analyzer.api.dto.revenue;

import com.analyzer.api.enums.ExpertPaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateExpertPaymentRequest {
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("1000000000.00")
    private BigDecimal consultationFee;

    @NotNull
    private ExpertPaymentStatus paymentStatus;
}
