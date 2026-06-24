package com.analyzer.api.dto.legalticket;

import com.fasterxml.jackson.annotation.JsonProperty;
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
@Schema(description = "Request to resolve a ticket by expert")
public class ResolveLegalTicketRequest {

    @NotBlank(message = "Nội dung phản hồi không được để trống")
    @JsonProperty("expert_answer")
    @Schema(description = "Official expert response to the customer", example = "Sau khi kiểm tra Điều 8.1, tôi nhận thấy phạt 30% là vi phạm Luật Thương mại 2005 (tối đa 8%).")
    private String expertAnswer;

    @JsonProperty("expert_internal_note")
    @Schema(description = "Internal notes accessible only to experts and admins", example = "Khách hàng này có gói Premium, cần ưu tiên rà soát nhanh.")
    private String expertInternalNote;
}
