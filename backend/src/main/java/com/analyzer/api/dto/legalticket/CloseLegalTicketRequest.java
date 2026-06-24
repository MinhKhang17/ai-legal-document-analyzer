package com.analyzer.api.dto.legalticket;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to close a ticket by customer")
public class CloseLegalTicketRequest {

    @Schema(description = "Feedback/notes from customer upon closing", example = "Cảm ơn chuyên gia, phản hồi rất chi tiết.")
    private String feedback;
}
