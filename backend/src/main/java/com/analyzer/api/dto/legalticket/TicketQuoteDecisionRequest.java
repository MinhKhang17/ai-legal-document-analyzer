package com.analyzer.api.dto.legalticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketQuoteDecisionRequest {
    @NotBlank @Pattern(regexp = "ACCEPT|REJECT") private String decision;
}
