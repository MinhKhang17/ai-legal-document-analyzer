package com.analyzer.api.dto.legalticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketPaymentConfirmationRequest {
    @NotBlank @Size(max = 255) private String paymentReference;
}
