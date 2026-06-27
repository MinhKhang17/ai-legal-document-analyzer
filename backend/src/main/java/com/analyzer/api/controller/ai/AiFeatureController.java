package com.analyzer.api.controller.ai;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.ai.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
public class AiFeatureController {

    @GetMapping("/tickets/{ticketId}/assessment")
    public ResponseEntity<ApiResponseDTO<AiRiskAssessmentResponse>> getTicketAssessment(
            @PathVariable("ticketId") String ticketId) {
        return notImplemented();
    }

    @GetMapping("/tickets/{ticketId}/summary")
    public ResponseEntity<ApiResponseDTO<AiFeatureSummaryResponse>> getTicketSummary(
            @PathVariable("ticketId") String ticketId) {
        return notImplemented();
    }

    @GetMapping("/tickets/{ticketId}/citations")
    public ResponseEntity<ApiResponseDTO<List<AiCitationResponse>>> getTicketCitations(
            @PathVariable("ticketId") String ticketId) {
        return notImplemented();
    }

    @GetMapping("/chats/{chatMessageId}/citations")
    public ResponseEntity<ApiResponseDTO<List<AiCitationResponse>>> getChatMessageCitations(
            @PathVariable("chatMessageId") String chatMessageId) {
        return notImplemented();
    }

    private <T> ResponseEntity<ApiResponseDTO<T>> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponseDTO.error(501, "Phase 2 contract only", null));
    }
}
