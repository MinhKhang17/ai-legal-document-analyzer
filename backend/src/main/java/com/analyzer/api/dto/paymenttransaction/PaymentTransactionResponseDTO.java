package com.analyzer.api.dto.paymenttransaction;

import com.analyzer.api.enums.PaymentMethod;
import com.analyzer.api.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "Response payload containing payment transaction details")
public class PaymentTransactionResponseDTO {

    @Schema(description = "Transaction ID", example = "1")
    private Long id;

    @Schema(description = "Customer ID", example = "2")
    private Long customerId;

    @Schema(description = "Subscription Plan ID", example = "3")
    private Long subscriptionPlanId;

    @Schema(description = "Subscription Plan Name", example = "Premium Plan")
    private String planName;

    @Schema(description = "Customer Plan ID", example = "4")
    private Long customerPlanId;

    @Schema(description = "Transaction amount", example = "299000")
    private BigDecimal amount;

    @Schema(description = "Payment method used", example = "BANK_TRANSFER")
    private PaymentMethod paymentMethod;

    @Schema(description = "Payment status", example = "PENDING")
    private PaymentStatus paymentStatus;

    @Schema(description = "Transaction reference code", example = "TX123456789")
    private String transactionCode;

    @Schema(description = "Transaction paid date time")
    private LocalDateTime paidAt;

    @Schema(description = "Creation date time")
    private LocalDateTime createdAt;

    @Schema(description = "Last update date time")
    private LocalDateTime updatedAt;
}
