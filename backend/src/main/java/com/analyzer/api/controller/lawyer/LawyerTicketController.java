package com.analyzer.api.controller.lawyer;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lawyer/tickets")
public class LawyerTicketController {

    @GetMapping("/my")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> getMyTickets() {
        return notImplemented();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> getDetail(@PathVariable("id") String ticketId) {
        return notImplemented();
    }

    @GetMapping("/{id}/files")
    public ResponseEntity<ApiResponseDTO<List<TicketFileResponse>>> getFiles(@PathVariable("id") String ticketId) {
        return notImplemented();
    }

    @PostMapping("/{id}/files")
    public ResponseEntity<ApiResponseDTO<TicketFileResponse>> uploadFile(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody UploadTicketFileRequest request) {
        return notImplemented();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponseDTO<List<LegalTicketMessageResponse>>> getMessages(
            @PathVariable("id") String ticketId) {
        return notImplemented();
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponseDTO<ChatWithUserResponse>> chatWithUser(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody ChatWithUserRequest request) {
        return notImplemented();
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> closeTicket(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody CloseLegalTicketRequest request) {
        return notImplemented();
    }

    @GetMapping("/{id}/download/{documentId}")
    public ResponseEntity<ApiResponseDTO<TicketFileResponse>> downloadUserFile(
            @PathVariable("id") String ticketId,
            @PathVariable("documentId") String documentId) {
        return notImplemented();
    }

    private <T> ResponseEntity<ApiResponseDTO<T>> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponseDTO.error(501, "Phase 2 contract only", null));
    }
}
