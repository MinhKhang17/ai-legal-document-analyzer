package com.analyzer.api.dto.legalticket;

import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateTicketMessageRequest {
    @Size(max = 5000) private String content;
    @Size(max = 100) private String clientMessageId;
    private String replyToMessageId;
    @Builder.Default private List<String> attachmentIds = List.of();
}
