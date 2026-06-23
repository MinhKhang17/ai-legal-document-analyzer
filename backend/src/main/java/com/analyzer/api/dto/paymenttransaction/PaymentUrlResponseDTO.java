package com.analyzer.api.dto.paymenttransaction;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment URL response")
public class PaymentUrlResponseDTO {

    @Schema(description = "Transaction ID", example = "1")
    private Long transactionId;

    @Schema(description = "Transaction reference code", example = "TX123456789")
    private String transactionCode;

    @Schema(description = "Payment gateway name", example = "VNPAY")
    private String provider;

    @Schema(description = "URL used by the customer to complete payment")
    private String paymentUrl;
}
