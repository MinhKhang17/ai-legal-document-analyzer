package com.analyzer.api.dto.legalticket;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketActionRequest {
    @NotBlank private String action;
    private String note;
}
