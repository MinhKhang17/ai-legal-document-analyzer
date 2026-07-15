package com.analyzer.api.service.ai;

import com.analyzer.api.dto.ai.AiCitationResponse;

import java.util.List;

public interface AiCitationService {

    List<AiCitationResponse> getTicketCitations(String ticketId);

    List<AiCitationResponse> getChatMessageCitations(String chatMessageId);
}
