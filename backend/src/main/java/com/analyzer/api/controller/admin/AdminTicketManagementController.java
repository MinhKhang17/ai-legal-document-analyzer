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
    private final com.analyzer.api.service.ExpertRevenueService expertRevenueService;
    private final com.analyzer.api.service.ExpertTicketWorkflowService expertTicketWorkflowService;

    @PostMapping("/{id}/classify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> classify(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody AdminTicketClassificationRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket classified",
                expertTicketWorkflowService.classify(getCurrentUserId(), ticketId, request)));
    }

    @PostMapping("/{id}/confirm-payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> confirmPayment(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody TicketPaymentConfirmationRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket payment confirmed",
                expertTicketWorkflowService.confirmPayment(getCurrentUserId(), ticketId, request)));
    }

    @PostMapping("/{id}/offer-assignment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> offerAssignment(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody AssignLawyerRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Assignment offered",
                expertTicketWorkflowService.offerAssignment(getCurrentUserId(), ticketId,
                        request.getLawyerId(), request.getAdminNote())));
    }

    @PostMapping("/{id}/extend-sla")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> extendSla(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody AdminSlaExtensionRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("SLA extended",
                expertTicketWorkflowService.extendSla(getCurrentUserId(), ticketId, request)));
    }

    @PatchMapping("/{id}/expert-payment")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update expert consultation fee and payment status")
    public ResponseEntity<ApiResponseDTO<com.analyzer.api.dto.revenue.ExpertRevenueTicketResponse>> updateExpertPayment(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody com.analyzer.api.dto.revenue.UpdateExpertPaymentRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Cap nhat thanh toan expert thanh cong",
                expertRevenueService.updatePayment(ticketId, getCurrentUserId(), request)));
    }

    @PostMapping("/{id}/expert-payment/reset")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset expert payment/commission data on a ticket",
            description = "Clears consultationFee, commissionRate, platformFee, expertPayout and paymentStatus so the ticket can be reassigned to a different expert. Blocked once paymentStatus is PAID.")
    public ResponseEntity<ApiResponseDTO<com.analyzer.api.dto.revenue.ExpertRevenueTicketResponse>> resetExpertPayment(
            @PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Da reset du lieu thanh toan expert",
                expertRevenueService.resetFinancials(ticketId, getCurrentUserId())));
    }

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
                expertTicketWorkflowService.offerAssignment(adminId, ticketId, request.getLawyerId(), request.getAdminNote())));
    }

    @PostMapping("/{id}/reassign-lawyer")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reassign lawyer to ticket")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> reassignLawyer(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody AssignLawyerRequest request) {
        Long adminId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Lawyer reassigned successfully",
                expertTicketWorkflowService.reassign(adminId, ticketId, request.getLawyerId(), request.getAdminNote())));
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
