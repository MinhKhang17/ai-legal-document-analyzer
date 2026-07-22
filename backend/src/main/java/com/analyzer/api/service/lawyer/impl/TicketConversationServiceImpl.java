package com.analyzer.api.service.lawyer.impl;

import com.analyzer.api.dto.legalticket.AdminChatHistoryResponse;
import com.analyzer.api.dto.legalticket.ChatWithUserRequest;
import com.analyzer.api.dto.legalticket.ChatWithUserResponse;
import com.analyzer.api.dto.legalticket.LegalTicketMessageResponse;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.LegalTicketMessage;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.LegalTicketMessageType;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.legalticket.LegalTicketMessageRepository;
import com.analyzer.api.repository.legalticket.LegalTicketRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.service.lawyer.TicketConversationService;
import com.analyzer.api.service.notification.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketConversationServiceImpl implements TicketConversationService {

    private static final List<LegalTicketStatus> CHATABLE_STATUSES = List.of(
            LegalTicketStatus.ASSIGNED_TO_LAWYER,
            LegalTicketStatus.ASSIGNED_TO_EXPERT,
            LegalTicketStatus.IN_REVIEW,
            LegalTicketStatus.NEED_MORE_INFO,
            LegalTicketStatus.CUSTOMER_RESPONDED,
            LegalTicketStatus.REOPENED);

    private final LegalTicketRepository legalTicketRepository;
    private final LegalTicketMessageRepository legalTicketMessageRepository;
    private final UserRepository userRepository;
    private final LegalTicketMapper legalTicketMapper;
    private final EmailService emailService;

    @Override
    @Transactional
    public ChatWithUserResponse chatWithUser(String ticketId, Long lawyerId, ChatWithUserRequest request) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        if (ticket.getAssignedLawyer() == null || !ticket.getAssignedLawyer().getId().equals(lawyerId)) {
            throw new ForbiddenException("Bạn không phải là Luật sư được phân công xử lý yêu cầu này");
        }

        if (!CHATABLE_STATUSES.contains(ticket.getStatus())) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        User lawyer = userRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Luật sư ID: " + lawyerId));

        String clientMessageId = normalizeClientMessageId(request.getClientMessageId());
        if (clientMessageId != null) {
            var existing = legalTicketMessageRepository
                    .findByTicket_IdAndSender_IdAndClientMessageId(ticketId, lawyerId, clientMessageId);
            if (existing.isPresent()) {
                return ChatWithUserResponse.builder().ticketId(ticketId).messageId(existing.get().getId())
                        .message(existing.get().getContent()).build();
            }
        }

        LegalTicketMessage message = LegalTicketMessage.builder()
                .ticket(ticket)
                .sender(lawyer)
                .clientMessageId(clientMessageId)
                .content(request.getMessage())
                .messageType(LegalTicketMessageType.EXPERT_RESPONSE)
                .internalOnly(false)
                .build();

        LegalTicketMessage savedMessage = legalTicketMessageRepository.save(message);

        if (ticket.getStatus() != LegalTicketStatus.NEED_MORE_INFO) {
            ticket.setStatus(LegalTicketStatus.IN_REVIEW);
        }
        ticket.setLastLawyerMessageAt(LocalDateTime.now());
        ticket.setLastExpertActivityAt(LocalDateTime.now());
        if (ticket.getFirstRespondedAt() == null) {
            ticket.setFirstRespondedAt(LocalDateTime.now());
        }
        legalTicketRepository.save(ticket);

        emailService.sendTicketNotificationAsync(ticket.getCreatedBy().getEmail(), ticket.getCreatedBy().getFirstName(),
                ticket.getId(), ticket.getTicketType() != null ? ticket.getTicketType().name() : "CONTACT_EXPERT",
                ticket.getStatus().name(), "/tickets/" + ticket.getId(), "Chuyen gia vua phan hoi ticket.");

        return ChatWithUserResponse.builder()
                .ticketId(ticket.getId())
                .messageId(savedMessage.getId())
                .message(savedMessage.getContent())
                .build();
    }

    private String normalizeClientMessageId(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
