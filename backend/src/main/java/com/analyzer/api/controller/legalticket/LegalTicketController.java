package com.analyzer.api.controller.legalticket;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.legalticket.LegalTicketService;
import com.analyzer.api.service.lawyer.TicketFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/legal-tickets")
@RequiredArgsConstructor
@Tag(name = "Customer Legal Tickets", description = "Customer endpoints for creating, inspecting, and managing tickets")
public class LegalTicketController {

    private final LegalTicketService legalTicketService;
    private final TicketFileService ticketFileService;
    private final com.analyzer.api.service.legalticket.ExpertTicketWorkflowService expertTicketWorkflowService;

    @PostMapping("/{id}/quote-decision")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> decideQuote(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody TicketQuoteDecisionRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Quote decision recorded",
                expertTicketWorkflowService.decideQuote(currentUser.getId(), ticketId, request)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Create legal ticket", description = "Creates a legal ticket from direct customer input or an AI suggestion.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> createTicket(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateLegalTicketRequest request) {
        return new ResponseEntity<>(
                ApiResponseDTO.created("Legal ticket created successfully", legalTicketService.createTicket(currentUser.getId(), request)),
                HttpStatus.CREATED);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get my legal tickets", description = "Returns a paginated list of tickets created by the current customer.")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> getMyTickets(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam(value = "status", required = false) LegalTicketStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponseDTO.success("Legal tickets retrieved successfully",
                legalTicketService.getMyTickets(currentUser.getId(), status, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EXPERT')")
    @Operation(summary = "Get legal ticket detail", description = "Returns ticket details.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> getTicket(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Legal ticket retrieved successfully",
                legalTicketService.getTicketById(currentUser.getId(), currentUser.getRoleName(), ticketId)));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EXPERT')")
    @Operation(summary = "Get legal ticket message history", description = "Returns message history for this ticket.")
    public ResponseEntity<ApiResponseDTO<List<LegalTicketMessageResponse>>> getMessages(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Legal ticket messages retrieved successfully",
                legalTicketService.getMessages(currentUser.getId(), currentUser.getRoleName(), ticketId)));
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get files shared with customer")
    public ResponseEntity<ApiResponseDTO<List<TicketFileResponse>>> getCustomerFiles(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket files retrieved successfully",
                ticketFileService.listCustomerVisibleFiles(ticketId, currentUser.getId())));
    }

    @GetMapping("/{id}/files/{documentId}/download")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Download a file shared with customer")
    public ResponseEntity<Resource> downloadCustomerFile(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @PathVariable String documentId) {
        Resource resource = ticketFileService.downloadCustomerVisibleFile(ticketId, currentUser.getId(), documentId);
        String filename = resource.getFilename() == null ? documentId : resource.getFilename();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename.replace("\"", "") + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Cancel ticket", description = "Allows a customer to cancel a ticket that is pending review.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> cancelTicket(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody(required = false) CancelLegalTicketRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket cancelled successfully",
                legalTicketService.cancelTicket(currentUser.getId(), ticketId, request)));
    }

    @PostMapping("/{id}/customer-reply")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Customer reply", description = "Allows a customer to add supplementary information or reply to the lawyer.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> customerReply(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody CustomerTicketReplyRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Reply submitted successfully",
                legalTicketService.customerReply(currentUser.getId(), ticketId, request)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Close ticket by customer", description = "Allows a customer to close a resolved ticket.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> closeTicket(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody(required = false) CloseLegalTicketRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket closed successfully",
                legalTicketService.closeTicket(currentUser.getId(), ticketId, request)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Reopen ticket", description = "Allows a customer to reopen a resolved or closed ticket within 7 days.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> reopenTicket(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String ticketId,
            @Valid @RequestBody ReopenLegalTicketRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket reopened successfully",
                legalTicketService.reopenTicket(currentUser.getId(), ticketId, request)));
    }
}
