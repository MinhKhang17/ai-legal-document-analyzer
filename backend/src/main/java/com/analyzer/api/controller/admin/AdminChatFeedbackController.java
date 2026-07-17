package com.analyzer.api.controller.admin;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackResponse;
import com.analyzer.api.service.admin.AdminChatFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/chat-messages")
@RequiredArgsConstructor
@Tag(name = "Admin Chat Feedback", description = "Endpoints for admin to review customer feedback on AI answers")
public class AdminChatFeedbackController {

    private final AdminChatFeedbackService adminChatFeedbackService;

    @GetMapping("/feedback")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List chat message feedback", description = "Paginated list of customer ratings/comments on AI assistant messages, optionally filtered by rating.")
    public ResponseEntity<ApiResponseDTO<PageResponse<ChatMessageFeedbackResponse>>> listFeedback(
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Feedback retrieved successfully",
                adminChatFeedbackService.listFeedback(rating, page, size)));
    }
}
