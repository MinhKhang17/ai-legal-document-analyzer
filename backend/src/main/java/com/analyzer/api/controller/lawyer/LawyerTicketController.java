package com.analyzer.api.controller.lawyer;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.LegalTicketService;
import com.analyzer.api.service.lawyer.TicketConversationService;
import com.analyzer.api.service.lawyer.TicketFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lawyer/tickets")
@RequiredArgsConstructor
@Tag(name = "Lawyer Ticket Operations", description = "Endpoints for assigned experts/lawyers to view, chat, and manage tickets")
public class LawyerTicketController {

    private final TicketConversationService ticketConversationService;
    private final TicketFileService ticketFileService;
    private final LegalTicketService legalTicketService;

    @GetMapping("/my")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Get tickets assigned to current expert/lawyer")
    public ResponseEntity<ApiResponseDTO<PageResponse<LegalTicketResponse>>> getMyTickets(
            @RequestParam(value = "status", required = false) LegalTicketStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Long lawyerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved assigned tickets successfully",
                legalTicketService.getMyTickets(lawyerId, status, page, size)));
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
    public ResponseEntity<ApiResponseDTO<List<TicketFileResponse>>> getFiles(@PathVariable("id") String ticketId) {
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
    public ResponseEntity<ApiResponseDTO<List<LegalTicketMessageResponse>>> getMessages(
            @PathVariable("id") String ticketId) {
        Long lawyerId = getCurrentUserId();
        String role = getCurrentUserRole();
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved messages successfully",
                legalTicketService.getMessages(lawyerId, role, ticketId)));
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
    @Operation(summary = "Close ticket")
    public ResponseEntity<ApiResponseDTO<LegalTicketResponse>> closeTicket(
            @PathVariable("id") String ticketId,
            @Valid @RequestBody CloseLegalTicketRequest request) {
        Long lawyerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Ticket closed successfully",
                legalTicketService.closeTicket(lawyerId, ticketId, request)));
    }

    @GetMapping("/{id}/download/{documentId}")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "Download file attached to ticket")
    public ResponseEntity<ApiResponseDTO<TicketFileResponse>> downloadUserFile(
            @PathVariable("id") String ticketId,
            @PathVariable("documentId") String documentId) {
        Long lawyerId = getCurrentUserId();
        List<TicketFileResponse> files = ticketFileService.listFiles(ticketId, lawyerId);
        TicketFileResponse file = files.stream()
                .filter(f -> f.getDocumentId().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Tài liệu không tồn tại hoặc không có quyền truy cập"));
        return ResponseEntity.ok(ApiResponseDTO.success("File info retrieved successfully", file));
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

    private String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }
        if (authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            if (userDetails.getAuthorities() != null && !userDetails.getAuthorities().isEmpty()) {
                GrantedAuthority authority = userDetails.getAuthorities().iterator().next();
                String roleWithPrefix = authority.getAuthority();
                return roleWithPrefix.startsWith("ROLE_") ? roleWithPrefix.substring(5) : roleWithPrefix;
            }
        }
        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
