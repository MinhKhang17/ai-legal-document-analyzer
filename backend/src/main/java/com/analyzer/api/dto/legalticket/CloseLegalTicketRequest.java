package com.analyzer.api.dto.legalticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
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

    @Size(max = 2000, message = "Phan hoi khong duoc vuot qua 2000 ky tu")
    @Schema(description = "Feedback/notes from customer upon closing", example = "Cảm ơn chuyên gia, phản hồi rất chi tiết.")
    private String feedback;
}
