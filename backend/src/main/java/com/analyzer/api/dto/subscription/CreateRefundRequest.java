package com.analyzer.api.dto.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRefundRequest {

    @NotNull
    private Long paymentTransactionId;

    private Long customerPlanId;

    @NotBlank
    @Size(max = 2000)
    private String reason;

    @NotNull
    @Positive
    private BigDecimal amount;

    @Size(max = 200) private String bankName;
    @Size(max = 100) private String accountNumber;
    @Size(max = 200) private String accountHolderName;
    @Size(max = 100) private String transactionId;
    @Size(max = 100) private String invoiceId;
    @Size(max = 2000) private String refundReason;
}
