package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.TicketComplexity;
import com.analyzer.api.enums.TicketPricingType;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminTicketClassificationRequest {
    @NotNull private TicketComplexity complexity;
    @NotBlank @Size(max = 2000) private String reason;
    @NotNull private Long proposedExpertId;
    @NotNull private TicketPricingType pricingType;
    @NotNull @DecimalMin("0.00") private BigDecimal userPrice;
    @NotNull @DecimalMin(value = "0.01") private BigDecimal internalTicketValue;
}
