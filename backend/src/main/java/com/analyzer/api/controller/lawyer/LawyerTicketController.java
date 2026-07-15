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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @GetMapping("/my")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Get tickets assigned to current expert/lawyer")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> getMyTickets(
            @RequestParam(value = "status", required = false) LegalTicketStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Long lawyerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved assigned tickets successfully",
                expertLegalTicketService.getAssignedTickets(lawyerId, status, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Get detailed information of assigned ticket")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> getDetail(@PathVariable("id") String ticketId) {
        Long lawyerId = getCurrentUserId();
        String role = getCurrentUserRole();
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved ticket details successfully",
                legalTicketService.getTicketById(lawyerId, role, ticketId)));
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "List files attached to ticket")
    public ResponseEntity<ApiResponseDTO<java.util.List<TicketFileResponse>>> getFiles(@PathVariable("id") String ticketId) {
        Long lawyerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved ticket files successfully",
                ticketFileService.listFiles(ticketId, lawyerId)));
    }

    @PostMapping("/{id}/files")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Upload attachment file for ticket")
    public ResponseEntity<ApiResponseDTO<TicketFileResponse>> uploadFile(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody UploadTicketFileRequest request) {
        Long lawyerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("File uploaded successfully",
                ticketFileService.uploadFile(ticketId, lawyerId, request)));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Get message history for ticket")
    public ResponseEntity<ApiResponseDTO<java.util.List<LegalTicketMessageResponse>>> getMessages(
            @PathVariable("id") String ticketId) {
        Long lawyerId = getCurrentUserId();
        String role = getCurrentUserRole();
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved messages successfully",
                legalTicketService.getMessages(lawyerId, role, ticketId)));
    }

    @PostMapping("/{id}/start-review")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Start reviewing the assigned ticket")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> startReview(@PathVariable("id") String ticketId) {
        Long lawyerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Review started successfully",
                expertLegalTicketService.startReview(lawyerId, ticketId)));
    }

    @PostMapping("/{id}/request-more-info")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Request more information from the customer")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> requestMoreInfo(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody RequestMoreInfoRequest request) {
        Long lawyerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("More information requested successfully",
                expertLegalTicketService.requestMoreInfo(lawyerId, ticketId, request)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Resolve the assigned ticket", description = "Allows the lawyer to send the final answer directly to the customer.")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> resolveTicket(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody ResolveLegalTicketRequest request) {
        Long lawyerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket resolved successfully",
                expertLegalTicketService.resolveTicket(lawyerId, ticketId, request)));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Send message to user on assigned ticket")
    public ResponseEntity<ApiResponseDTO<ChatWithUserResponse>> chatWithUser(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody ChatWithUserRequest request) {
        Long lawyerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Message sent successfully",
                ticketConversationService.chatWithUser(ticketId, lawyerId, request)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Resolve ticket")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> closeTicket(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody CloseLegalTicketRequest request) {
        Long lawyerId = getCurrentUserId();
        ResolveLegalTicketRequest resolveRequest = ResolveLegalTicketRequest.builder()
                .expertAnswer(request.getFeedback())
                .expertInternalNote(null)
                .build();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket resolved successfully",
                expertLegalTicketService.resolveTicket(lawyerId, ticketId, resolveRequest)));
    }

    @GetMapping("/{id}/download/{documentId}")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Download file attached to ticket")
    public ResponseEntity<Resource> downloadUserFile(
            @PathVariable("id") String ticketId,
            @PathVariable("documentId") String documentId) {
        Long lawyerId = getCurrentUserId();
        Resource resource = ticketFileService.downloadFile(ticketId, lawyerId, documentId);
        String filename = resource.getFilename() == null ? documentId : resource.getFilename();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Ban chua dang nhap");
        }
        if (authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thong tin xac thuc khong hop le");
    }

    private String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Ban chua dang nhap");
        }
        if (authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            if (userDetails.getAuthorities() != null && !userDetails.getAuthorities().isEmpty()) {
                GrantedAuthority authority = userDetails.getAuthorities().iterator().next();
                String roleWithPrefix = authority.getAuthority();
                return roleWithPrefix.startsWith("ROLE_") ? roleWithPrefix.substring(5) : roleWithPrefix;
            }
        }
        throw new RuntimeException("Thong tin xac thuc khong hop le");
    }
}
