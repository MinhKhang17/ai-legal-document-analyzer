package com.analyzer.api.dto.legalticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for customer replying to a ticket message thread")
public class CustomerTicketReplyRequest {

    @NotBlank(message = "Nội dung phản hồi không được để trống")
    @Schema(description = "Message content reply to the expert", example = "Giá trị hợp đồng là 500 triệu đồng.")
    private String message;

    @Size(max = 100)
    private String clientMessageId;
}
