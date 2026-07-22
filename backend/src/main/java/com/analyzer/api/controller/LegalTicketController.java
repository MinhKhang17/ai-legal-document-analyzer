package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.LegalTicketService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/legal-tickets")
@RequiredArgsConstructor
@Tag(name = "Customer Legal Tickets", description = "Customer endpoints for creating, inspecting, and managing tickets")
public class LegalTicketController {

    private final LegalTicketService legalTicketService;
    private final TicketFileService ticketFileService;
    private final com.analyzer.api.service.ExpertTicketWorkflowService expertTicketWorkflowService;

    @PostMapping("/{id}/quote-decision")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> decideQuote(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody TicketQuoteDecisionRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Quote decision recorded",
                expertTicketWorkflowService.decideQuote(getCurrentUserId(), ticketId, request)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Create legal ticket", description = "Creates a legal ticket from direct customer input or an AI suggestion.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> createTicket(@Valid @RequestBody CreateLegalTicketRequest request) {
        Long currentUserId = getCurrentUserId();
        return new ResponseEntity<>(
                ApiResponseDTO.created("Legal ticket created successfully", legalTicketService.createTicket(currentUserId, request)),
                HttpStatus.CREATED);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get my legal tickets", description = "Returns a paginated list of tickets created by the current customer.")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> getMyTickets(
            @RequestParam(value = "status", required = false) LegalTicketStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Legal tickets retrieved successfully",
                legalTicketService.getMyTickets(currentUserId, status, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EXPERT')")
    @Operation(summary = "Get legal ticket detail", description = "Returns ticket details.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> getTicket(@PathVariable("id") String ticketId) {
        Long currentUserId = getCurrentUserId();
        String currentUserRole = getCurrentUserRole();
        return ResponseEntity.ok(ApiResponseDTO.success("Legal ticket retrieved successfully",
                legalTicketService.getTicketById(currentUserId, currentUserRole, ticketId)));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EXPERT')")
    @Operation(summary = "Get legal ticket message history", description = "Returns message history for this ticket.")
    public ResponseEntity<ApiResponseDTO<List<LegalTicketMessageResponse>>> getMessages(@PathVariable("id") String ticketId) {
        Long currentUserId = getCurrentUserId();
        String currentUserRole = getCurrentUserRole();
        return ResponseEntity.ok(ApiResponseDTO.success("Legal ticket messages retrieved successfully",
                legalTicketService.getMessages(currentUserId, currentUserRole, ticketId)));
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get files shared with customer")
    public ResponseEntity<ApiResponseDTO<List<TicketFileResponse>>> getCustomerFiles(
            @PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket files retrieved successfully",
                ticketFileService.listCustomerVisibleFiles(ticketId, getCurrentUserId())));
    }

    @GetMapping("/{id}/files/{documentId}/download")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Download a file shared with customer")
    public ResponseEntity<Resource> downloadCustomerFile(
            @PathVariable("id") String ticketId,
            @PathVariable String documentId) {
        Resource resource = ticketFileService.downloadCustomerVisibleFile(ticketId, getCurrentUserId(), documentId);
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
            @PathVariable("id") String ticketId,
            @RequestBody(required = false) CancelLegalTicketRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket cancelled successfully",
                legalTicketService.cancelTicket(currentUserId, ticketId, request)));
    }

    @PostMapping("/{id}/customer-reply")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Customer reply", description = "Allows a customer to add supplementary information or reply to the lawyer.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> customerReply(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody CustomerTicketReplyRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Reply submitted successfully",
                legalTicketService.customerReply(currentUserId, ticketId, request)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Close ticket by customer", description = "Allows a customer to close a resolved ticket.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> closeTicket(
            @PathVariable("id") String ticketId,
            @RequestBody(required = false) CloseLegalTicketRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket closed successfully",
                legalTicketService.closeTicket(currentUserId, ticketId, request)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Reopen ticket", description = "Allows a customer to reopen a resolved or closed ticket within 7 days.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> reopenTicket(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody ReopenLegalTicketRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket reopened successfully",
                legalTicketService.reopenTicket(currentUserId, ticketId, request)));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }

    private String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            if (userDetails.getAuthorities() != null && !userDetails.getAuthorities().isEmpty()) {
                String roleWithPrefix = userDetails.getAuthorities().iterator().next().getAuthority();
                return roleWithPrefix.startsWith("ROLE_") ? roleWithPrefix.substring(5) : roleWithPrefix;
            }
        }
        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
