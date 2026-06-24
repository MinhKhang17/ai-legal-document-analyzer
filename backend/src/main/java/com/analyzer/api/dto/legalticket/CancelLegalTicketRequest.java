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
@Schema(description = "Request to cancel a ticket by customer")
public class CancelLegalTicketRequest {

    @Schema(description = "Cancellation reason", example = "Tôi tự thương lượng được rồi, không cần hỗ trợ nữa.")
    private String reason;
}
