package com.analyzer.api.service.lawyer;

import com.analyzer.api.dto.legalticket.ChatWithUserRequest;
import com.analyzer.api.dto.legalticket.ChatWithUserResponse;
import com.analyzer.api.dto.legalticket.AdminChatHistoryResponse;

public interface TicketConversationService {

    ChatWithUserResponse chatWithUser(String ticketId, Long lawyerId, ChatWithUserRequest request);

    AdminChatHistoryResponse getChatHistory(String ticketId);
}
