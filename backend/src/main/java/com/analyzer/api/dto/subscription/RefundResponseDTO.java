package com.analyzer.api.dto.subscription;

import com.analyzer.api.enums.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponseDTO {

    private Long id;
    private Long paymentTransactionId;
    private Long customerPlanId;
    private Long requestedById;
    private String reason;
    private RefundStatus status;
    private BigDecimal amount;
    private String adminNote;
    private String legalTicketId;
    private String bankName;
    private String accountNumber;
    private String accountHolderName;
    private String invoiceId;
    private LocalDateTime confirmationExpiresAt;
    private LocalDateTime emailConfirmedAt;
    private Boolean emailConfirmed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
