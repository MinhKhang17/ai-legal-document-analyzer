package com.analyzer.api.controller.lawyer;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.ExpertLegalTicketService;
import com.analyzer.api.service.LegalTicketService;
import com.analyzer.api.service.lawyer.TicketConversationService;
import com.analyzer.api.service.lawyer.TicketFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lawyer/tickets")
@RequiredArgsConstructor
@Tag(name = "Lawyer Ticket Operations", description = "Endpoints for assigned experts/lawyers to view, chat, and manage tickets")
public class LawyerTicketController {

    private final TicketConversationService ticketConversationService;
    private final TicketFileService ticketFileService;
    private final LegalTicketService legalTicketService;
    private final ExpertLegalTicketService expertLegalTicketService;
    private final com.analyzer.api.service.ExpertTicketWorkflowService expertTicketWorkflowService;

    @PostMapping("/{id}/assessment")
    @PreAuthorize("hasRole('EXPERT')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> assess(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody ExpertTicketAssessmentRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Assessment recorded",
                expertTicketWorkflowService.assess(currentUser.getId(), ticketId, request)));
    }

    @PostMapping("/{id}/assignment-decision")
    @PreAuthorize("hasRole('EXPERT')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> decideAssignment(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody ExpertAssignmentDecisionRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Assignment decision recorded",
                expertTicketWorkflowService.decideAssignment(currentUser.getId(), ticketId, request)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Get tickets assigned to current expert/lawyer")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> getMyTickets(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam(value = "status", required = false) LegalTicketStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved assigned tickets successfully",
                expertLegalTicketService.getAssignedTickets(currentUser.getId(), status, page, size)));
    }

    @GetMapping("/proposed")
    @PreAuthorize("hasRole('EXPERT')")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> getProposedTickets(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved proposed tickets",
                expertLegalTicketService.getProposedTickets(currentUser.getId(), page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Get detailed information of assigned ticket")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> getDetail(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved ticket details successfully",
                legalTicketService.getTicketById(currentUser.getId(), currentUser.getRoleName(), ticketId)));
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "List files attached to ticket")
    public ResponseEntity<ApiResponseDTO<java.util.List<TicketFileResponse>>> getFiles(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved ticket files successfully",
                ticketFileService.listFiles(ticketId, currentUser.getId())));
    }

    @PostMapping("/{id}/files")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Upload attachment file for ticket")
    public ResponseEntity<ApiResponseDTO<TicketFileResponse>> uploadFile(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody UploadTicketFileRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("File uploaded successfully",
                ticketFileService.uploadFile(ticketId, currentUser.getId(), request)));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Get message history for ticket")
    public ResponseEntity<ApiResponseDTO<java.util.List<LegalTicketMessageResponse>>> getMessages(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved messages successfully",
                legalTicketService.getMessages(currentUser.getId(), currentUser.getRoleName(), ticketId)));
    }

    @PostMapping("/{id}/start-review")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Start reviewing the assigned ticket")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> startReview(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Review started successfully",
                expertLegalTicketService.startReview(currentUser.getId(), ticketId)));
    }

    @PostMapping("/{id}/request-more-info")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Request more information from the customer")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> requestMoreInfo(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody RequestMoreInfoRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("More information requested successfully",
                expertLegalTicketService.requestMoreInfo(currentUser.getId(), ticketId, request)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Resolve the assigned ticket", description = "Allows the lawyer to send the final answer directly to the customer.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> resolveTicket(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody ResolveLegalTicketRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket resolved successfully",
                expertLegalTicketService.resolveTicket(currentUser.getId(), ticketId, request)));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Send message to user on assigned ticket")
    public ResponseEntity<ApiResponseDTO<ChatWithUserResponse>> chatWithUser(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody ChatWithUserRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Message sent successfully",
                ticketConversationService.chatWithUser(ticketId, currentUser.getId(), request)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Resolve ticket")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> closeTicket(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody CloseLegalTicketRequest request) {
        ResolveLegalTicketRequest resolveRequest = ResolveLegalTicketRequest.builder()
                .expertAnswer(request.getFeedback())
                .expertInternalNote(null)
                .build();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket resolved successfully",
                expertLegalTicketService.resolveTicket(currentUser.getId(), ticketId, resolveRequest)));
    }

    @GetMapping("/{id}/download/{documentId}")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Download file attached to ticket")
    public ResponseEntity<Resource> downloadUserFile(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @PathVariable("documentId") String documentId) {
        Resource resource = ticketFileService.downloadFile(ticketId, currentUser.getId(), documentId);
        String filename = resource.getFilename() == null ? documentId : resource.getFilename();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
