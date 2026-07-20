package com.analyzer.api.controller.admin;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketType;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.LegalTicketService;
import com.analyzer.api.service.admin.AdminTicketManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tickets")
@RequiredArgsConstructor
@Tag(name = "Admin Ticket Management", description = "Endpoints for admins to manage, review, and assign tickets")
public class AdminTicketManagementController {

    private final AdminTicketManagementService adminTicketManagementService;
    private final LegalTicketService legalTicketService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "View list of all legal tickets for admin")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> viewTickets(
            @RequestParam(value = "status", required = false) LegalTicketStatus status,
            @RequestParam(value = "riskLevel", required = false) RiskLevel riskLevel,
            @RequestParam(value = "ticketType", required = false) LegalTicketType ticketType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved tickets list successfully",
                legalTicketService.listAdminTickets(status, riskLevel, ticketType, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "View ticket details for admin")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> viewTicket(@PathVariable("id") String ticketId) {
        Long adminId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved ticket details successfully",
                legalTicketService.getTicketById(adminId, "ADMIN", ticketId)));
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "View AI summary for ticket")
    public ResponseEntity<ApiResponseDTO<TicketSummaryResponse>> viewAiSummary(@PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved AI summary successfully",
                adminTicketManagementService.viewAiSummary(ticketId)));
    }

    @GetMapping("/{id}/chat-history")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "View complete chat history for ticket")
    public ResponseEntity<ApiResponseDTO<AdminChatHistoryResponse>> viewChatHistory(@PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved chat history successfully",
                adminTicketManagementService.viewChatHistory(ticketId)));
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "View files submitted by user for ticket")
    public ResponseEntity<ApiResponseDTO<List<AdminUserFileResponse>>> viewUserFiles(@PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved user files successfully",
                adminTicketManagementService.viewUserFiles(ticketId)));
    }

    @PostMapping("/{id}/assign-lawyer")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign lawyer to ticket")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> assignLawyer(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody AssignLawyerRequest request) {
        Long adminId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Lawyer assigned successfully",
                adminTicketManagementService.assignLawyer(ticketId, adminId, request)));
    }

    @PostMapping("/{id}/reassign-lawyer")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reassign lawyer to ticket")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> reassignLawyer(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody AssignLawyerRequest request) {
        Long adminId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Lawyer reassigned successfully",
                adminTicketManagementService.reassignLawyer(ticketId, adminId, request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject legal ticket", description = "Rejects a ticket during admin review.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> rejectTicket(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody RejectLegalTicketRequest request) {
        Long adminId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket rejected successfully",
                legalTicketService.rejectTicket(adminId, ticketId, request)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> approveTicket(@PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket approved",
                adminTicketManagementService.approveInternal(ticketId, getCurrentUserId())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> closeTicket(
            @PathVariable("id") String ticketId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket closed",
                adminTicketManagementService.closeInternal(ticketId, getCurrentUserId(), body == null ? null : body.get("note"))));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }
        if (authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
