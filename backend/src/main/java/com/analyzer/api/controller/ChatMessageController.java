package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackRequest;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageResponse;
import com.analyzer.api.dto.chatmessage.SendMessageRequest;
import com.analyzer.api.dto.chatmessage.SendMessageResponse;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Chat Message Management", description = "APIs for sending and retrieving chat and RAG messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping("/workspaces/{workspaceId}/messages")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Send first message in workspace", description = "Send first query message in a workspace, which automatically initializes a default chat session if not present.")
    public ResponseEntity<ApiResponseDTO<SendMessageResponse>> sendFirstMessageInWorkspace(
            @PathVariable String workspaceId,
            @Valid @RequestBody SendMessageRequest request) {
        SendMessageResponse data = chatMessageService.sendMessageInWorkspace(getCurrentUserId(), workspaceId, request);
        return ResponseEntity.ok(ApiResponseDTO.success("Message processed successfully", data));
    }

    @PostMapping("/chat-sessions/{chatSessionId}/messages")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Send message in chat session", description = "Send a follow-up query message in an existing active chat session.")
    public ResponseEntity<ApiResponseDTO<SendMessageResponse>> sendMessageInChatSession(
            @PathVariable String chatSessionId,
            @Valid @RequestBody SendMessageRequest request) {
        SendMessageResponse data = chatMessageService.sendMessageInChatSession(getCurrentUserId(), chatSessionId, request);
        return ResponseEntity.ok(ApiResponseDTO.success("Message processed successfully", data));
    }

    @GetMapping("/chat-sessions/{chatSessionId}/messages")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get messages in chat session", description = "Retrieve a paginated list of chat messages for a chat session, sorted chronologically.")
    public ResponseEntity<ApiResponseDTO<PageResponse<ChatMessageResponse>>> getMessagesByChatSession(
            @PathVariable String chatSessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<ChatMessageResponse> data = chatMessageService.getMessagesByChatSession(getCurrentUserId(), chatSessionId, page, size);
        return ResponseEntity.ok(ApiResponseDTO.success("Messages retrieved successfully", data));
    }

    @GetMapping("/chat-messages/{messageId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get message details", description = "Retrieve details and metadata for a specific chat message.")
    public ResponseEntity<ApiResponseDTO<ChatMessageResponse>> getMessageDetail(
            @PathVariable String messageId) {
        ChatMessageResponse data = chatMessageService.getMessageDetail(getCurrentUserId(), messageId);
        return ResponseEntity.ok(ApiResponseDTO.success("Message retrieved successfully", data));
    }

    @PostMapping("/chat-messages/{messageId}/feedback")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Rate an AI assistant message", description = "Submit or update a rating/comment for an AI assistant chat message.")
    public ResponseEntity<ApiResponseDTO<ChatMessageFeedbackResponse>> submitFeedback(
            @PathVariable String messageId,
            @Valid @RequestBody ChatMessageFeedbackRequest request) {
        ChatMessageFeedbackResponse data = chatMessageService.submitFeedback(getCurrentUserId(), messageId, request);
        return ResponseEntity.ok(ApiResponseDTO.success("Feedback submitted successfully", data));
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
