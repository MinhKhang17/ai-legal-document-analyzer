package com.analyzer.api.service.lawyer.impl;

import com.analyzer.api.dto.legalticket.AdminChatHistoryResponse;
import com.analyzer.api.dto.legalticket.ChatWithUserRequest;
import com.analyzer.api.dto.legalticket.ChatWithUserResponse;
import com.analyzer.api.dto.legalticket.LegalTicketMessageResponse;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.LegalTicketMessage;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.LegalTicketMessageType;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.LegalTicketMessageRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.lawyer.TicketConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketConversationServiceImpl implements TicketConversationService {

    private final LegalTicketRepository legalTicketRepository;
    private final LegalTicketMessageRepository legalTicketMessageRepository;
    private final UserRepository userRepository;
    private final LegalTicketMapper legalTicketMapper;

    @Override
    @Transactional
    public ChatWithUserResponse chatWithUser(String ticketId, Long lawyerId, ChatWithUserRequest request) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        if (ticket.getAssignedLawyer() == null || !ticket.getAssignedLawyer().getId().equals(lawyerId)) {
            throw new ForbiddenException("Bạn không phải là Luật sư được phân công xử lý yêu cầu này");
        }

        User lawyer = userRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Luật sư ID: " + lawyerId));

        LegalTicketMessage message = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(lawyer)
                .content(request.getMessage())
                .messageType(LegalTicketMessageType.EXPERT_RESPONSE)
                .internalOnly(false)
                .build();

        LegalTicketMessage savedMessage = legalTicketMessageRepository.save(message);

        ticket.setLastLawyerMessageAt(LocalDateTime.now());
        legalTicketRepository.save(ticket);

        return ChatWithUserResponse.builder()
                .ticketId(ticket.getId())
                .messageId(savedMessage.getId())
                .message(savedMessage.getContent())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminChatHistoryResponse getChatHistory(String ticketId) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        List<LegalTicketMessage> messages = legalTicketMessageRepository.findByTicket_IdOrderByCreatedAtAsc(ticketId);
        List<LegalTicketMessageResponse> messageResponses = messages.stream()
                .map(legalTicketMapper::toMessageResponse)
                .toList();

        return AdminChatHistoryResponse.builder()
                .ticketId(ticket.getId())
                .messages(messageResponses)
                .build();
    }
}
