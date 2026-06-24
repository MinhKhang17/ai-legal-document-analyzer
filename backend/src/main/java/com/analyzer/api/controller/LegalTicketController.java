package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.legalticket.CreateLegalTicketRequest;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.service.AdminTicketAssignmentService;
import com.analyzer.api.service.LegalTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ticket endpoints for customers and admins.
 * The implementation is intentionally thin until the ticket repository is introduced.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Legal Tickets", description = "Create and inspect lawyer tickets generated from AI uncertainty")
public class LegalTicketController {

    private final LegalTicketService legalTicketService;
    private final AdminTicketAssignmentService adminTicketAssignmentService;

    @PostMapping("/api/v1/legal-tickets")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Create legal ticket", description = "Creates a ticket when the AI suggests human legal review.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> createTicket(@Valid @RequestBody CreateLegalTicketRequest request) {
        return new ResponseEntity<>(
                ApiResponseDTO.created("Legal ticket created successfully", legalTicketService.createTicket(request)),
                HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/legal-tickets/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Get legal ticket", description = "Returns a ticket read model by id.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> getTicket(@PathVariable("id") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Legal ticket retrieved successfully", legalTicketService.getTicketById(ticketId)));
    }

    @GetMapping("/api/v1/admin/legal-tickets")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List admin legal tickets", description = "Returns the admin view of legal tickets.")
    public ResponseEntity<ApiResponseDTO<List<LegalTicketResponse>>> listAdminTickets() {
        return ResponseEntity.ok(ApiResponseDTO.success("Legal tickets retrieved successfully", legalTicketService.listAdminTickets()));
    }

    @PostMapping("/api/v1/admin/legal-tickets/{id}/assign-lawyer")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign lawyer to ticket", description = "Marks a ticket as assigned to a lawyer.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> assignLawyer(
            @PathVariable("id") String ticketId,
            @RequestParam("lawyerId") String lawyerId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Lawyer assigned successfully", adminTicketAssignmentService.assignLawyer(ticketId, lawyerId)));
    }
}
