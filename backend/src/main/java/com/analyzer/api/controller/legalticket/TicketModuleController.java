package com.analyzer.api.controller.legalticket;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.lawyer.ExpertLegalTicketService;
import com.analyzer.api.service.legalticket.ExpertTicketWorkflowService;
import com.analyzer.api.service.legalticket.LegalTicketService;
import com.analyzer.api.service.legalticket.TicketCollaborationService;
import com.analyzer.api.service.*;
import com.analyzer.api.service.admin.AdminTicketManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final ExpertTicketWorkflowService expertTicketWorkflowService;

    @GetMapping("/config/attachment-policy")
    public ResponseEntity<ApiResponseDTO<AttachmentPolicyResponse>> attachmentPolicy() {
        return ResponseEntity.ok(ApiResponseDTO.success("Attachment policy", collaborationService.policy()));
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<TicketAttachmentResponse>> uploadAttachment(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "ownerId", required = false) String ownerId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.created("Attachment uploaded",
                collaborationService.upload(currentUser.getId(), ownerId, file)));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<Void>> deleteAttachment(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable String attachmentId) {
        collaborationService.remove(currentUser.getId(), attachmentId);
        return ResponseEntity.ok(ApiResponseDTO.success("Attachment removed", null));
    }

    @GetMapping("/attachments/{attachmentId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadAttachment(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable String attachmentId) {
        Resource resource = collaborationService.download(currentUser.getId(), currentUser.getRoleName(), attachmentId);
        String filename = collaborationService.attachmentFilename(attachmentId).replace("\"", "");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(resource);
    }

    @PostMapping("/tickets")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> createTicket(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateLegalTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.created("Ticket created",
                legalTicketService.createTicket(currentUser.getId(), request)));
    }

    @GetMapping("/tickets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> listTickets(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam(value = "status", required = false) LegalTicketStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        String role = currentUser.getRoleName();
        PageResponse<LegalTicketResponse> result = "ADMIN".equals(role)
                ? legalTicketService.listAdminTickets(status, null, null, page, size)
                : "EXPERT".equals(role)
                    ? expertLegalTicketService.getAssignedTickets(currentUser.getId(), status, page, size)
                    : legalTicketService.getMyTickets(currentUser.getId(), status, page, size);
        return ResponseEntity.ok(ApiResponseDTO.success("Tickets retrieved", result));
    }

    @GetMapping("/tickets/{ticketId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> ticketDetail(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket retrieved",
                legalTicketService.getTicketById(currentUser.getId(), currentUser.getRoleName(), ticketId)));
    }

    @PatchMapping("/tickets/{ticketId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> transition(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable String ticketId, @Valid @RequestBody TicketActionRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket updated",
                collaborationService.transition(currentUser.getId(), currentUser.getRoleName(), ticketId, request)));
    }

    @PostMapping("/tickets/{ticketId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<LegalTicketMessageResponse>> addMessage(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable String ticketId, @Valid @RequestBody CreateTicketMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.created("Message added",
                collaborationService.addMessage(currentUser.getId(), currentUser.getRoleName(), ticketId, request)));
    }

    @GetMapping("/tickets/{ticketId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketMessageResponse>>> messages(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable String ticketId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponseDTO.success("Messages retrieved",
                collaborationService.messages(currentUser.getId(), currentUser.getRoleName(), ticketId, page, size)));
    }

    @PostMapping("/tickets/{ticketId}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> assign(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable String ticketId, @Valid @RequestBody AssignLawyerRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket assigned",
                expertTicketWorkflowService.offerAssignment(currentUser.getId(), ticketId,
                        request.getLawyerId(), request.getAdminNote())));
    }

    @PostMapping("/tickets/{ticketId}/shares")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<ConversationShareResponse>> createShare(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable String ticketId, @Valid @RequestBody CreateConversationShareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.created("Share created",
                collaborationService.createShare(currentUser.getId(), currentUser.getRoleName(), ticketId, request)));
    }

    @DeleteMapping("/tickets/{ticketId}/shares/{shareId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<Void>> revokeShare(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable String ticketId, @PathVariable String shareId) {
        collaborationService.revokeShare(currentUser.getId(), currentUser.getRoleName(), ticketId, shareId);
        return ResponseEntity.ok(ApiResponseDTO.success("Share revoked", null));
    }

    @GetMapping("/shared-conversation/{token}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> sharedConversation(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable String token) {
        return ResponseEntity.ok(ApiResponseDTO.success("Shared conversation retrieved",
                collaborationService.openShare(currentUser.getId(), currentUser.getRoleName(), token)));
    }
}
