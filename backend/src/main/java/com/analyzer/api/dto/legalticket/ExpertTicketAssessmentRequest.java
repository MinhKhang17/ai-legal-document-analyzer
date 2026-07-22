package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.TicketComplexity;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExpertTicketAssessmentRequest {
    @NotBlank @Pattern(regexp = "ACCEPT|DECLINE|REQUEST_RECLASSIFICATION") private String decision;
    @Size(max = 2000) private String reason;
    private TicketComplexity proposedType;
    @Min(0) private Integer estimatedWorkload;
    @Min(0) private Integer documentCount;
    @Min(0) private Integer issueCount;
    @Min(0) private Integer expectedDurationHours;
    @DecimalMin("0.00") private BigDecimal requestedMinimumPayout;
}
