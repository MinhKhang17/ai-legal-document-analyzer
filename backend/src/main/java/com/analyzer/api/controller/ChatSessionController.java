package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatsession.ChatSessionResponse;
import com.analyzer.api.dto.chatsession.CreateChatSessionRequest;
import com.analyzer.api.dto.chatsession.ShareChatSessionResponse;
import com.analyzer.api.enums.ChatSessionStatus;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Chat Session Management", description = "APIs for managing chat sessions in a workspace")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @PostMapping("/api/v1/workspaces/{workspaceId}/chat-sessions")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create chat session", description = "Create a new chat session in the workspace of the current authenticated user.")
    public ResponseEntity<ApiResponseDTO<ChatSessionResponse>> createChatSession(
            @PathVariable String workspaceId,
            @Valid @RequestBody CreateChatSessionRequest request) {
        ChatSessionResponse response = chatSessionService.createChatSession(getCurrentUserId(), workspaceId, request);
        return new ResponseEntity<>(
                ApiResponseDTO.created("Chat session created successfully", response),
                HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/chat-sessions")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get workspace chat sessions", description = "Retrieves a paginated list of chat sessions in the workspace for the authenticated customer.")
    public ResponseEntity<ApiResponseDTO<PageResponse<ChatSessionResponse>>> getChatSessionsByWorkspace(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {

        ChatSessionStatus chatSessionStatus;
        if (status == null || status.trim().isEmpty()) {
            chatSessionStatus = ChatSessionStatus.ACTIVE;
        } else {
            try {
                chatSessionStatus = ChatSessionStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new com.analyzer.api.exception.validation.InvalidStatusException("Invalid chat session status");
            }
        }

        PageResponse<ChatSessionResponse> response = chatSessionService.getChatSessionsByWorkspace(
                getCurrentUserId(),
                workspaceId,
                page,
                size,
                chatSessionStatus);

        return ResponseEntity.ok(ApiResponseDTO.success("Chat sessions retrieved successfully", response));
    }

    @GetMapping("/api/v1/chat-sessions/{chatSessionId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get chat session detail", description = "Retrieves the details of a specific chat session for the authenticated customer.")
    public ResponseEntity<ApiResponseDTO<ChatSessionResponse>> getChatSessionDetail(
            @PathVariable String chatSessionId) {
        ChatSessionResponse response = chatSessionService.getChatSessionDetail(getCurrentUserId(), chatSessionId);
        return ResponseEntity.ok(ApiResponseDTO.success("Chat session retrieved successfully", response));
    }

    @PutMapping("/api/v1/chat-sessions/{chatSessionId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Update chat session", description = "Updates the title of a specific chat session for the authenticated customer.")
    public ResponseEntity<ApiResponseDTO<ChatSessionResponse>> updateChatSession(
            @PathVariable String chatSessionId,
            @Valid @RequestBody com.analyzer.api.dto.chatsession.UpdateChatSessionRequest request) {
        ChatSessionResponse response = chatSessionService.updateChatSession(getCurrentUserId(), chatSessionId, request);
        return ResponseEntity.ok(ApiResponseDTO.success("Chat session updated successfully", response));
    }

    @DeleteMapping("/api/v1/chat-sessions/{chatSessionId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Delete chat session", description = "Soft deletes a specific chat session by updating its status to DELETED.")
    public ResponseEntity<ApiResponseDTO<com.analyzer.api.dto.chatsession.DeleteChatSessionResponse>> deleteChatSession(
            @PathVariable String chatSessionId) {
        com.analyzer.api.dto.chatsession.DeleteChatSessionResponse response = chatSessionService.deleteChatSession(getCurrentUserId(), chatSessionId);
        return ResponseEntity.ok(ApiResponseDTO.success("Chat session deleted successfully", response));
    }

    @PostMapping("/api/v1/chat-sessions/{chatSessionId}/share")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Share chat session", description = "Generates (or reuses) a read-only share link for Admin/Expert to view this chat session's history.")
    public ResponseEntity<ApiResponseDTO<ShareChatSessionResponse>> shareChatSession(
            @PathVariable String chatSessionId) {
        ShareChatSessionResponse response = chatSessionService.shareChatSession(getCurrentUserId(), chatSessionId);
        return ResponseEntity.ok(ApiResponseDTO.success("Chat session shared successfully", response));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
