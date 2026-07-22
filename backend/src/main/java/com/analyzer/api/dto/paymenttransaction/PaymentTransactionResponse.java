package com.analyzer.api.dto.paymenttransaction;

import com.analyzer.api.enums.PaymentMethod;
import com.analyzer.api.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Response payload containing payment transaction details")
public record PaymentTransactionResponse(
        @Schema(description = "Transaction ID", example = "1")
        Long id,

        @Schema(description = "Customer ID", example = "2")
        Long customerId,

        @Schema(description = "Subscription Plan ID", example = "3")
        Long subscriptionPlanId,

        @Schema(description = "Subscription Plan Name", example = "Premium Plan")
        String planName,

        @Schema(description = "Customer Plan ID", example = "4")
        Long customerPlanId,

        @Schema(description = "Transaction amount", example = "299000")
        BigDecimal amount,

        @Schema(description = "Payment method used", example = "BANK_TRANSFER")
        PaymentMethod paymentMethod,

        @Schema(description = "Payment status", example = "PENDING")
        PaymentStatus paymentStatus,

        @Schema(description = "Transaction reference code", example = "TX123456789")
        String transactionCode,

        @Schema(description = "URL used by the customer to complete payment")
        String paymentUrl,

        @Schema(description = "Gateway transaction number")
        String gatewayTransactionNo,

        @Schema(description = "Gateway response code")
        String gatewayResponseCode,

        @Schema(description = "Transaction paid date time")
        LocalDateTime paidAt,

        @Schema(description = "Creation date time")
        LocalDateTime createdAt,

        @Schema(description = "Last update date time")
        LocalDateTime updatedAt
) {
}
