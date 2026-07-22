package com.analyzer.api.service.admin.impl;

import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.LegalTicketMessage;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketMessageType;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.ExpertPaymentStatus;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.LegalTicketMessageRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.admin.AdminTicketManagementService;
import com.analyzer.api.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTicketManagementServiceImpl implements AdminTicketManagementService {

    private static final List<LegalTicketStatus> ASSIGNABLE_STATUSES = List.of(
            LegalTicketStatus.DRAFT,
            LegalTicketStatus.PENDING_ADMIN_REVIEW,
            LegalTicketStatus.ASSIGNED_TO_LAWYER,
            LegalTicketStatus.IN_REVIEW,
            LegalTicketStatus.NEED_MORE_INFO,
            LegalTicketStatus.CUSTOMER_RESPONDED,
            LegalTicketStatus.REOPENED);

    private final LegalTicketRepository legalTicketRepository;
    private final LegalTicketMessageRepository legalTicketMessageRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final LegalTicketMapper legalTicketMapper;
    private final EmailService emailService;

    @Override
    @Transactional
    public LegalTicketResponse assignLawyer(String ticketId, Long adminId, AssignLawyerRequest request) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        if (ticket.getTicketType() == com.analyzer.api.enums.LegalTicketType.REFUND_REQUEST) {
            throw new ConflictException("REFUND_TICKET_ADMIN_ONLY");
        }
        if (!ASSIGNABLE_STATUSES.contains(ticket.getStatus())) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }
        if (ticket.getAssignedLawyer() != null && !Boolean.TRUE.equals(request.getForceReassign())) {
            throw new ConflictException("Ticket đã được phân công cho Luật sư ID: " + ticket.getAssignedLawyer().getId());
        }

        if (ticket.getAssignedLawyer() != null
                && ticket.getAssignedLawyer().getId().equals(request.getLawyerId())) {
            return legalTicketMapper.toResponse(ticket);
        }
        if (Boolean.TRUE.equals(request.getForceReassign()) && hasPaymentData(ticket)) {
            throw new ConflictException("CANNOT_REASSIGN_TICKET_WITH_PAYMENT_SET");
        }

        User lawyer = userRepository.findById(request.getLawyerId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Luật sư ID: " + request.getLawyerId()));

        if (!lawyer.isActive() || lawyer.getRole() == null || lawyer.getRole().getName() != RoleName.EXPERT) {
            throw new ConflictException("ASSIGNEE_MUST_BE_ACTIVE_EXPERT");
        }

        ticket.setAssignedLawyer(lawyer);
        ticket.setAssignedAt(java.time.LocalDateTime.now());
        if (ticket.getStatus() == LegalTicketStatus.PENDING_ADMIN_REVIEW || ticket.getStatus() == LegalTicketStatus.DRAFT) {
            ticket.setStatus(LegalTicketStatus.ASSIGNED_TO_LAWYER);
        }

        LegalTicket savedTicket = legalTicketRepository.save(ticket);
        String ticketType = savedTicket.getTicketType() != null ? savedTicket.getTicketType().name() : "CONTACT_EXPERT";
        emailService.sendTicketNotificationAsync(lawyer.getEmail(), lawyer.getFirstName(), savedTicket.getId(),
                ticketType, savedTicket.getStatus().name(), "/lawyer/tickets/" + savedTicket.getId(),
                "Ban vua duoc admin phan cong ticket.");
        emailService.sendTicketNotificationAsync(savedTicket.getCreatedBy().getEmail(), savedTicket.getCreatedBy().getFirstName(), savedTicket.getId(),
                ticketType, savedTicket.getStatus().name(), "/tickets/" + savedTicket.getId(),
                "Ticket da duoc phan cong cho chuyen gia.");
        return legalTicketMapper.toResponse(savedTicket);
    }

    private boolean hasPaymentData(LegalTicket ticket) {
        return ticket.getExpertPaymentStatus() != ExpertPaymentStatus.UNPAID
                || (ticket.getConsultationFee() != null
                    && ticket.getConsultationFee().compareTo(BigDecimal.ZERO) > 0);
    }

    @Override
    @Transactional
    public LegalTicketResponse reassignLawyer(String ticketId, Long adminId, AssignLawyerRequest request) {
        request.setForceReassign(true);
        return assignLawyer(ticketId, adminId, request);
    }

    @Override
    @Transactional
    public LegalTicketResponse approveInternal(String ticketId, Long adminId) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));
        if (ticket.getStatus() != LegalTicketStatus.PENDING_ADMIN_REVIEW) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("ADMIN_NOT_FOUND"));
        ticket.setStatus(LegalTicketStatus.IN_REVIEW);
        legalTicketMessageRepository.save(LegalTicketMessage.builder().ticket(ticket).sender(admin)
                .content("Admin da tiep nhan va dang xu ly noi bo.")
                .messageType(LegalTicketMessageType.SYSTEM).internalOnly(false).build());
        return legalTicketMapper.toResponse(legalTicketRepository.save(ticket));
    }

    @Override
    @Transactional
    public LegalTicketResponse closeInternal(String ticketId, Long adminId, String note) {
        LegalTicket ticket = legalTicketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("ADMIN_NOT_FOUND"));
        if (note == null || note.isBlank()) {
            throw new ConflictException("ADMIN_CLOSE_REASON_REQUIRED");
        }
        if (ticket.getStatus() == LegalTicketStatus.CLOSED || ticket.getStatus() == LegalTicketStatus.CANCELLED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }
        if (ticket.getAssignedLawyer() != null && ticket.getStatus() != LegalTicketStatus.RESOLVED) {
            throw new ConflictException("TICKET_NOT_RESOLVED_BY_EXPERT");
        }
        ticket.setStatus(LegalTicketStatus.CLOSED);
        ticket.setClosedAt(java.time.LocalDateTime.now());
        ticket.setAdminNote(note);
        legalTicketMessageRepository.save(LegalTicketMessage.builder().ticket(ticket).sender(admin)
                .content("Admin da dong ticket." + (note == null || note.isBlank() ? "" : " " + note))
                .messageType(LegalTicketMessageType.SYSTEM).internalOnly(false).build());
        LegalTicket saved = legalTicketRepository.save(ticket);
        emailService.sendTicketNotificationAsync(saved.getCreatedBy().getEmail(), saved.getCreatedBy().getFirstName(),
                saved.getId(), saved.getTicketType() != null ? saved.getTicketType().name() : "CONTACT_EXPERT",
                saved.getStatus().name(), "/tickets/" + saved.getId(), note);
        return legalTicketMapper.toResponse(saved);
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
