package com.analyzer.api.controller.admin;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatmessage.AiFeedbackSummaryResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackResponse;
import com.analyzer.api.enums.AiFeedbackType;
import com.analyzer.api.enums.ChatMode;
import com.analyzer.api.enums.FeedbackRating;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.service.admin.AdminChatFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminChatFeedbackController {
    private final AdminChatFeedbackService adminChatFeedbackService;

    @GetMapping({"/ai-feedbacks", "/chat-messages/feedback"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<PageResponse<ChatMessageFeedbackResponse>>> listFeedback(
            @RequestParam(required = false) AiFeedbackType feedbackType,
            @RequestParam(required = false) FeedbackRating rating,
            @RequestParam(required = false) ChatMode resolvedMode,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponseDTO.success("Feedback retrieved successfully",
                adminChatFeedbackService.listFeedback(feedbackType, rating, resolvedMode, riskLevel,
                        fromDate, toDate, keyword, page, size)));
    }

    @GetMapping("/ai-feedbacks/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<AiFeedbackSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponseDTO.success("Feedback summary retrieved successfully",
                adminChatFeedbackService.getSummary()));
    }
}
