package com.analyzer.api.service.admin.impl;

import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.LegalTicketMessage;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.LegalTicketMessageRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.admin.AdminTicketManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTicketManagementServiceImpl implements AdminTicketManagementService {

    private final LegalTicketRepository legalTicketRepository;
    private final LegalTicketMessageRepository legalTicketMessageRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final LegalTicketMapper legalTicketMapper;

    @Override
    @Transactional
    public LegalTicketResponse assignLawyer(String ticketId, Long adminId, AssignLawyerRequest request) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        if (ticket.getAssignedLawyer() != null && !Boolean.TRUE.equals(request.getForceReassign())) {
            throw new ConflictException("Ticket đã được phân công cho Luật sư ID: " + ticket.getAssignedLawyer().getId());
        }

        User lawyer = userRepository.findById(request.getLawyerId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Luật sư ID: " + request.getLawyerId()));

        ticket.setAssignedLawyer(lawyer);
        if (ticket.getStatus() == LegalTicketStatus.PENDING_ADMIN_REVIEW || ticket.getStatus() == LegalTicketStatus.DRAFT) {
            ticket.setStatus(LegalTicketStatus.ASSIGNED_TO_LAWYER);
        }

        LegalTicket savedTicket = legalTicketRepository.save(ticket);
        return legalTicketMapper.toResponse(savedTicket);
    }

    @Override
    @Transactional
    public LegalTicketResponse reassignLawyer(String ticketId, Long adminId, AssignLawyerRequest request) {
        request.setForceReassign(true);
        return assignLawyer(ticketId, adminId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketSummaryResponse viewAiSummary(String ticketId) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        return TicketSummaryResponse.builder()
                .ticketId(ticket.getId())
                .confidenceScore(ticket.getConfidenceScore() != null ? ticket.getConfidenceScore() : 0.85)
                .riskLevel(ticket.getRiskLevel() != null ? ticket.getRiskLevel() : RiskLevel.MEDIUM)
                .suggestionType(ticket.getSuggestionType() != null ? ticket.getSuggestionType() : SuggestionType.SUGGEST_LAWYER)
                .suggestionReason(ticket.getSuggestionReason() != null ? ticket.getSuggestionReason() : "Phân tích AI cho thấy điều khoản hỗ trợ có một số rủi ro pháp lý cần luật sư xem xét.")
                .summary(ticket.getCustomerNote() != null ? ticket.getCustomerNote() : ticket.getQuestion())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminChatHistoryResponse viewChatHistory(String ticketId) {
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

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserFileResponse> viewUserFiles(String ticketId) {
        legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        List<Document> documents = documentRepository.findByLegalTicket_Id(ticketId);

        return documents.stream()
                .map(doc -> AdminUserFileResponse.builder()
                        .documentId(doc.getId())
                        .originalFileName(doc.getOriginalFileName())
                        .fileType(doc.getFileType())
                        .fileSize(doc.getFileSize())
                        .visibilityScope(doc.getVisibilityScope())
                        .uploadedAt(doc.getUploadedAt() != null ? doc.getUploadedAt() : doc.getUpdatedAt())
                        .build())
                .toList();
    }
}
