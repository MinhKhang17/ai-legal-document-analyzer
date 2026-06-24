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
@Schema(description = "Request to reopen a ticket by customer")
public class ReopenLegalTicketRequest {

    @NotBlank(message = "Lý do mở lại không được để trống")
    @Schema(description = "Reopen reason", example = "Chuyên gia giải thích phần này chưa rõ lắm.")
    private String reason;
}
