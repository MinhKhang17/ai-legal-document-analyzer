package com.analyzer.api.dto.legalticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatWithUserResponse {

    private String ticketId;
    private String messageId;
    private String message;
}
