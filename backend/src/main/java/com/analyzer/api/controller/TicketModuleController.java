package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.*;
import com.analyzer.api.service.admin.AdminTicketManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TicketModuleController {
    private final TicketCollaborationService collaborationService;
    private final LegalTicketService legalTicketService;
    private final ExpertLegalTicketService expertLegalTicketService;
    private final AdminTicketManagementService adminTicketManagementService;

    @GetMapping("/config/attachment-policy")
    public ApiResponseDTO<AttachmentPolicyResponse> attachmentPolicy() {
        return ApiResponseDTO.success("Attachment policy", collaborationService.policy());
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseDTO<TicketAttachmentResponse>> uploadAttachment(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "ownerId", required = false) String ownerId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.created("Attachment uploaded",
                collaborationService.upload(currentUserId(), ownerId, file)));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ApiResponseDTO<Void> deleteAttachment(@PathVariable String attachmentId) {
        collaborationService.remove(currentUserId(), attachmentId);
        return ApiResponseDTO.success("Attachment removed", null);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String attachmentId) {
        Resource resource = collaborationService.download(currentUserId(), currentRole(), attachmentId);
        String filename = collaborationService.attachmentFilename(attachmentId).replace("\"", "");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(resource);
    }

    @PostMapping("/tickets")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> createTicket(@Valid @RequestBody CreateLegalTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.created("Ticket created",
                legalTicketService.createTicket(currentUserId(), request)));
    }

    @GetMapping("/tickets")
    public ApiResponseDTO<PageResponse<LegalTicketResponse>> listTickets(
            @RequestParam(value = "status", required = false) LegalTicketStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        String role = currentRole();
        PageResponse<LegalTicketResponse> result = "ADMIN".equals(role)
                ? legalTicketService.listAdminTickets(status, null, null, page, size)
                : "EXPERT".equals(role)
                    ? expertLegalTicketService.getAssignedTickets(currentUserId(), status, page, size)
                    : legalTicketService.getMyTickets(currentUserId(), status, page, size);
        return ApiResponseDTO.success("Tickets retrieved", result);
    }

    @GetMapping("/tickets/{ticketId}")
    public ApiResponseDTO<LegalTicketResponse> ticketDetail(@PathVariable String ticketId) {
        return ApiResponseDTO.success("Ticket retrieved", legalTicketService.getTicketById(currentUserId(), currentRole(), ticketId));
    }

    @PatchMapping("/tickets/{ticketId}")
    public ApiResponseDTO<LegalTicketResponse> transition(
            @PathVariable String ticketId, @Valid @RequestBody TicketActionRequest request) {
        return ApiResponseDTO.success("Ticket updated",
                collaborationService.transition(currentUserId(), currentRole(), ticketId, request));
    }

    @PostMapping("/tickets/{ticketId}/messages")
    public ResponseEntity<ApiResponseDTO<LegalTicketMessageResponse>> addMessage(
            @PathVariable String ticketId, @Valid @RequestBody CreateTicketMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.created("Message added",
                collaborationService.addMessage(currentUserId(), currentRole(), ticketId, request)));
    }

    @GetMapping("/tickets/{ticketId}/messages")
    public ApiResponseDTO<PageResponse<LegalTicketMessageResponse>> messages(
            @PathVariable String ticketId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "30") int size) {
        return ApiResponseDTO.success("Messages retrieved", collaborationService.messages(currentUserId(), currentRole(), ticketId, page, size));
    }

    @PostMapping("/tickets/{ticketId}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<LegalTicketResponse> assign(@PathVariable String ticketId, @Valid @RequestBody AssignLawyerRequest request) {
        return ApiResponseDTO.success("Ticket assigned", adminTicketManagementService.assignLawyer(ticketId, currentUserId(), request));
    }

    @PostMapping("/tickets/{ticketId}/shares")
    public ResponseEntity<ApiResponseDTO<ConversationShareResponse>> createShare(
            @PathVariable String ticketId, @Valid @RequestBody CreateConversationShareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.created("Share created",
                collaborationService.createShare(currentUserId(), currentRole(), ticketId, request)));
    }

    @DeleteMapping("/tickets/{ticketId}/shares/{shareId}")
    public ApiResponseDTO<Void> revokeShare(@PathVariable String ticketId, @PathVariable String shareId) {
        collaborationService.revokeShare(currentUserId(), currentRole(), ticketId, shareId);
        return ApiResponseDTO.success("Share revoked", null);
    }

    @GetMapping("/shared-conversation/{token}")
    public ApiResponseDTO<LegalTicketResponse> sharedConversation(@PathVariable String token) {
        return ApiResponseDTO.success("Shared conversation retrieved",
                collaborationService.openShare(currentUserId(), currentRole(), token));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl details) return details.getId();
        throw new ForbiddenException("AUTHENTICATION_REQUIRED");
    }

    private String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) return auth.getAuthorities().stream()
                .map(item -> item.getAuthority().replace("ROLE_", "")).findFirst().orElseThrow();
        throw new ForbiddenException("AUTHENTICATION_REQUIRED");
    }
}
