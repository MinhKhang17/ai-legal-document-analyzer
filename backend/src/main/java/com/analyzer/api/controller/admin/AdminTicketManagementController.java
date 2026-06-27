package com.analyzer.api.controller.admin;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tickets")
public class AdminTicketManagementController {

    @GetMapping
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> viewTickets() {
        return notImplemented();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> viewTicket(@PathVariable("id") String ticketId) {
        return notImplemented();
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<ApiResponseDTO<TicketSummaryResponse>> viewAiSummary(@PathVariable("id") String ticketId) {
        return notImplemented();
    }

    @GetMapping("/{id}/chat-history")
    public ResponseEntity<ApiResponseDTO<AdminChatHistoryResponse>> viewChatHistory(@PathVariable("id") String ticketId) {
        return notImplemented();
    }

    @GetMapping("/{id}/files")
    public ResponseEntity<ApiResponseDTO<List<AdminUserFileResponse>>> viewUserFiles(@PathVariable("id") String ticketId) {
        return notImplemented();
    }

    @PostMapping("/{id}/assign-lawyer")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> assignLawyer(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody AssignLawyerRequest request) {
        return notImplemented();
    }

    @PostMapping("/{id}/reassign-lawyer")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> reassignLawyer(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody AssignLawyerRequest request) {
        return notImplemented();
    }

    private <T> ResponseEntity<ApiResponseDTO<T>> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponseDTO.error(501, "Phase 2 contract only", null));
    }
}
