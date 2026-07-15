package com.analyzer.api.dto.subscription;

import com.analyzer.api.enums.RefundStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRefundStatusRequest(
        @NotNull RefundStatus status,
        @Size(max = 2000) String adminNote
) {
}
