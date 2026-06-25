package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.AdminTicketAssignmentService;
import com.analyzer.api.service.ExpertLegalTicketService;
import com.analyzer.api.service.LegalTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Legal Tickets", description = "Create and inspect lawyer tickets generated from AI uncertainty")
public class LegalTicketController {

    private final LegalTicketService legalTicketService;
    private final AdminTicketAssignmentService adminTicketAssignmentService;
    private final ExpertLegalTicketService expertLegalTicketService;

    // --- Customer API Endpoints ---

    @PostMapping("/api/v1/legal-tickets")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Create legal ticket", description = "Creates a ticket when the AI suggests human legal review.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> createTicket(@Valid @RequestBody CreateLegalTicketRequest request) {
        Long currentUserId = getCurrentUserId();
        return new ResponseEntity<>(
                ApiResponseDTO.created("Legal ticket created successfully", legalTicketService.createTicket(currentUserId, request)),
                HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/legal-tickets/my")
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

    @GetMapping("/api/v1/legal-tickets/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EXPERT')")
    @Operation(summary = "Get legal ticket detail", description = "Returns a ticket details. Customers can only view their own tickets.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> getTicket(@PathVariable("id") String ticketId) {
        Long currentUserId = getCurrentUserId();
        String currentUserRole = getCurrentUserRole();
        return ResponseEntity.ok(ApiResponseDTO.success("Legal ticket retrieved successfully",
                legalTicketService.getTicketById(currentUserId, currentUserRole, ticketId)));
    }

    @GetMapping("/api/v1/legal-tickets/{id}/messages")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EXPERT')")
    @Operation(summary = "Get legal ticket message history", description = "Returns message history for this ticket. Internal notes are hidden for customers.")
    public ResponseEntity<ApiResponseDTO<List<LegalTicketMessageResponse>>> getMessages(@PathVariable("id") String ticketId) {
        Long currentUserId = getCurrentUserId();
        String currentUserRole = getCurrentUserRole();
        return ResponseEntity.ok(ApiResponseDTO.success("Legal ticket messages retrieved successfully",
                legalTicketService.getMessages(currentUserId, currentUserRole, ticketId)));
    }

    @PostMapping("/api/v1/legal-tickets/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Cancel ticket", description = "Allows a customer to cancel a ticket that is pending review.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> cancelTicket(
            @PathVariable("id") String ticketId,
            @RequestBody(required = false) CancelLegalTicketRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket cancelled successfully",
                legalTicketService.cancelTicket(currentUserId, ticketId, request)));
    }

    @PostMapping("/api/v1/legal-tickets/{id}/customer-reply")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Customer reply", description = "Allows a customer to add supplementary information or reply to the lawyer.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> customerReply(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody CustomerTicketReplyRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Reply submitted successfully",
                legalTicketService.customerReply(currentUserId, ticketId, request)));
    }

    @PostMapping("/api/v1/legal-tickets/{id}/close")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Close ticket", description = "Allows a customer to close a resolved ticket.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> closeTicket(
            @PathVariable("id") String ticketId,
            @RequestBody(required = false) CloseLegalTicketRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket closed successfully",
                legalTicketService.closeTicket(currentUserId, ticketId, request)));
    }

    @PostMapping("/api/v1/legal-tickets/{id}/reopen")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Reopen ticket", description = "Allows a customer to reopen a resolved or closed ticket within 7 days.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> reopenTicket(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody ReopenLegalTicketRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket reopened successfully",
                legalTicketService.reopenTicket(currentUserId, ticketId, request)));
    }

    // --- Admin API Endpoints ---

    @GetMapping("/api/v1/admin/legal-tickets")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List admin legal tickets", description = "Returns the admin view of legal tickets, with status/risk filters.")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> listAdminTickets(
            @RequestParam(value = "status", required = false) LegalTicketStatus status,
            @RequestParam(value = "riskLevel", required = false) RiskLevel riskLevel,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponseDTO.success("Legal tickets retrieved successfully",
                legalTicketService.listAdminTickets(status, riskLevel, page, size)));
    }

    @PostMapping("/api/v1/admin/legal-tickets/{id}/assign-lawyer")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign lawyer to ticket", description = "Marks a ticket as assigned to a lawyer.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> assignLawyer(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody AssignLawyerRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Lawyer assigned successfully",
                adminTicketAssignmentService.assignLawyer(currentUserId, ticketId, request)));
    }

    @PostMapping("/api/v1/admin/legal-tickets/{id}/reassign-lawyer")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reassign lawyer to ticket", description = "Re-assigns a ticket to another lawyer.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> reassignLawyer(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody AssignLawyerRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Lawyer re-assigned successfully",
                adminTicketAssignmentService.reassignLawyer(currentUserId, ticketId, request)));
    }

    @PostMapping("/api/v1/admin/legal-tickets/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject legal ticket", description = "Rejects a ticket during admin review.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> rejectTicket(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody RejectLegalTicketRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket rejected successfully",
                legalTicketService.rejectTicket(currentUserId, ticketId, request)));
    }

    // --- Expert/Lawyer API Endpoints ---

    @GetMapping("/api/v1/expert/legal-tickets/my-assigned")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Get my assigned tickets", description = "Returns a paginated list of tickets assigned to the current lawyer.")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> getAssignedTickets(
            @RequestParam(value = "status", required = false) LegalTicketStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Assigned tickets retrieved successfully",
                expertLegalTicketService.getAssignedTickets(currentUserId, status, page, size)));
    }

    @PostMapping("/api/v1/expert/legal-tickets/{id}/start-review")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Start ticket review", description = "Marks an assigned ticket status as IN_REVIEW.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> startReview(@PathVariable("id") String ticketId) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Review started successfully",
                expertLegalTicketService.startReview(currentUserId, ticketId)));
    }

    @PostMapping("/api/v1/expert/legal-tickets/{id}/request-more-info")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Request more information", description = "Asks customer to provide further details or documents.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> requestMoreInfo(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody RequestMoreInfoRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Information request submitted successfully",
                expertLegalTicketService.requestMoreInfo(currentUserId, ticketId, request)));
    }

    @PostMapping("/api/v1/expert/legal-tickets/{id}/resolve")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Resolve ticket", description = "Submits the expert answer and resolves the ticket.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> resolveTicket(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody ResolveLegalTicketRequest request) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket resolved successfully",
                expertLegalTicketService.resolveTicket(currentUserId, ticketId, request)));
    }

    // --- Authentication context helpers ---

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
                GrantedAuthority authority = userDetails.getAuthorities().iterator().next();
                String roleWithPrefix = authority.getAuthority();
                if (roleWithPrefix.startsWith("ROLE_")) {
                    return roleWithPrefix.substring(5);
                }
                return roleWithPrefix;
            }
        }

        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
