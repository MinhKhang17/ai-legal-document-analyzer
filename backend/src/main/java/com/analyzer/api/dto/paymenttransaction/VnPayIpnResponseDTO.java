package com.analyzer.api.dto.paymenttransaction;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "VNPAY IPN response")
public record VnPayIpnResponseDTO(
        @JsonProperty("RspCode")
        @Schema(description = "VNPAY response code", example = "00")
        String rspCode,

        @JsonProperty("Message")
        @Schema(description = "VNPAY response message", example = "Confirm Success")
        String message
) {
}
