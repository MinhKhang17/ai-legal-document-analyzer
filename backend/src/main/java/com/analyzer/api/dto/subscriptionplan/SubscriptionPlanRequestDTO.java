package com.analyzer.api.dto.subscriptionplan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "Request payload for creating/updating a subscription plan")
public class SubscriptionPlanRequestDTO {

    @NotBlank(message = "Tên gói không được để trống")
    @Schema(description = "Name of the subscription plan", example = "Premium Plan")
    private String planName;

    @NotBlank(message = "Loại gói không được để trống")
    @Schema(description = "Type of the subscription plan", example = "MONTHLY")
    private String planType;

    @Schema(description = "Description of the plan features", example = "Unlimited legal analysis and chat")
    private String description;

    @NotNull(message = "Giá không được để trống")
    @Schema(description = "Price of the plan", example = "299000")
    private BigDecimal price;

    @NotNull(message = "Thời lượng gói (ngày) không được để trống")
    @Schema(description = "Duration of the plan in days", example = "30")
    private Integer durationDays;

    @NotNull(message = "Quota tối đa không được để trống")
    @Schema(description = "Maximum quota of services", example = "100")
    private Integer maxQuota;

    @Schema(description = "Status of the plan", example = "true")
    private Boolean active;
}
