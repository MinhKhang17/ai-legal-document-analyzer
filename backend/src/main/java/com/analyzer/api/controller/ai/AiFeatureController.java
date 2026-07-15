package com.analyzer.api.controller.ai;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.ai.*;
import com.analyzer.api.service.ai.AiCitationService;
import com.analyzer.api.service.ai.AiFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Feature Integration", description = "Endpoints for retrieving AI assessments, summaries, and legal citations")
public class AiFeatureController {

    private final AiFeatureService aiFeatureService;
    private final AiCitationService aiCitationService;

    @GetMapping("/tickets/{ticketId}/assessment")
    @Operation(summary = "Get AI risk assessment for ticket")
    public ResponseEntity<ApiResponseDTO<AiRiskAssessmentResponse>> getTicketAssessment(
            @PathVariable("ticketId") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved AI risk assessment successfully",
                aiFeatureService.getTicketAssessment(ticketId)));
    }

    @GetMapping("/tickets/{ticketId}/summary")
    @Operation(summary = "Get AI summary for ticket")
    public ResponseEntity<ApiResponseDTO<AiFeatureSummaryResponse>> getTicketSummary(
            @PathVariable("ticketId") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved AI summary successfully",
                aiFeatureService.getTicketSummary(ticketId)));
    }

    @GetMapping("/tickets/{ticketId}/citations")
    @Operation(summary = "Get AI legal citations for ticket")
    public ResponseEntity<ApiResponseDTO<List<AiCitationResponse>>> getTicketCitations(
            @PathVariable("ticketId") String ticketId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved ticket citations successfully",
                aiCitationService.getTicketCitations(ticketId)));
    }

    @GetMapping("/chats/{chatMessageId}/citations")
    @Operation(summary = "Get AI legal citations for chat message")
    public ResponseEntity<ApiResponseDTO<List<AiCitationResponse>>> getChatMessageCitations(
            @PathVariable("chatMessageId") String chatMessageId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved chat message citations successfully",
                aiCitationService.getChatMessageCitations(chatMessageId)));
    }
}
