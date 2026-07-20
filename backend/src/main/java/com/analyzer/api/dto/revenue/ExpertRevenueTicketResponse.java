package com.analyzer.api.dto.revenue;

import com.analyzer.api.enums.ExpertPaymentStatus;
import com.analyzer.api.enums.LegalTicketStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExpertRevenueTicketResponse {
    private String ticketId;
    private String ticketCode;
    private LegalTicketStatus ticketStatus;
    private BigDecimal consultationFee;
    private ExpertPaymentStatus paymentStatus;
    private LocalDateTime resolvedAt;
    private LocalDateTime paidAt;
}
