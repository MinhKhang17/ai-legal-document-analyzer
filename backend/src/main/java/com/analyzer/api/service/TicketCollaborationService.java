package com.analyzer.api.service;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.AttachmentPolicyResponse;
import com.analyzer.api.dto.legalticket.ConversationShareResponse;
import com.analyzer.api.dto.legalticket.CreateConversationShareRequest;
import com.analyzer.api.dto.legalticket.CreateTicketMessageRequest;
import com.analyzer.api.dto.legalticket.LegalTicketMessageResponse;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.dto.legalticket.TicketActionRequest;
import com.analyzer.api.dto.legalticket.TicketAttachmentResponse;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.User;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TicketCollaborationService {
    AttachmentPolicyResponse policy();

    TicketAttachmentResponse upload(Long userId, String ownerId, MultipartFile file);

    void remove(Long userId, String attachmentId);

    LegalTicketMessageResponse addMessage(
            Long userId, String role, String ticketId, CreateTicketMessageRequest request);

    PageResponse<LegalTicketMessageResponse> messages(
            Long userId, String role, String ticketId, int page, int size);

    ConversationShareResponse createShare(
            Long userId, String role, String ticketId, CreateConversationShareRequest request);

    void revokeShare(Long userId, String role, String ticketId, String shareId);

    LegalTicketResponse openShare(Long userId, String role, String token);

    Resource download(Long userId, String role, String attachmentId);

    String attachmentFilename(String attachmentId);

    void claimForTicket(List<String> ids, Long userId, String ticketId);

    void auditTicket(LegalTicket ticket, User actor, String action, String metadataJson);

    LegalTicketResponse transition(Long userId, String role, String ticketId, TicketActionRequest request);

    LegalTicketResponse enrich(LegalTicketResponse response, LegalTicket ticket);
}
