package com.analyzer.api.dto.paymenttransaction;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payment URL response")
public record PaymentUrlResponseDTO(
        @Schema(description = "Transaction ID", example = "1")
        Long transactionId,

        @Schema(description = "Transaction reference code", example = "TX123456789")
        String transactionCode,

        @Schema(description = "Payment gateway name", example = "VNPAY")
        String provider,

        @Schema(description = "URL used by the customer to complete payment")
        String paymentUrl
) {
}
