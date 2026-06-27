package com.analyzer.api.dto.legalticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminChatHistoryResponse {

    private String ticketId;
    private List<LegalTicketMessageResponse> messages;
}
