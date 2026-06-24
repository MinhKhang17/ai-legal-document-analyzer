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
@Schema(description = "Request to ask customer for more info by expert")
public class RequestMoreInfoRequest {

    @NotBlank(message = "Nội dung yêu cầu không được để trống")
    @Schema(description = "Question/details needed from customer", example = "Vui lòng cung cấp thêm thông tin về giá trị hợp đồng thực tế để tôi đánh giá.")
    private String message;
}
