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
@Schema(description = "Request to cancel a ticket by customer")
public class CancelLegalTicketRequest {

    @Size(max = 2000, message = "Ly do khong duoc vuot qua 2000 ky tu")
    @Schema(description = "Cancellation reason", example = "Tôi tự thương lượng được rồi, không cần hỗ trợ nữa.")
    private String reason;
}
