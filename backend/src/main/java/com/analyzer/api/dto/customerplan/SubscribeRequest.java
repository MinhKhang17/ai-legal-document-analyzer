package com.analyzer.api.dto.customerplan;

import com.analyzer.api.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request payload for subscribing to a plan")
public class SubscribeRequest {

    @NotNull(message = "ID gói không được để trống")
    @Schema(description = "ID of the subscription plan to subscribe", example = "1")
    private Long subscriptionPlanId;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    @Schema(description = "Payment method selected", example = "VNPAY")
    private PaymentMethod paymentMethod;
}
