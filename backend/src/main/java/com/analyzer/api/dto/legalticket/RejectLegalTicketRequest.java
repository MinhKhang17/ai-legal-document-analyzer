package com.analyzer.api.dto.legalticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to reject a ticket by admin")
public class RejectLegalTicketRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    @Schema(description = "Reason for rejecting the ticket", example = "Yêu cầu không liên quan đến rà soát hợp đồng.")
    private String reason;
}
