package com.analyzer.api.controller.feedback;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.feedback.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class FeedbackController {

    @PostMapping("/admin/feedback/surveys")
    public ResponseEntity<ApiResponseDTO<SurveyResponseDTO>> createSurvey(
            @Valid @RequestBody CreateSurveyRequest request) {
        return notImplemented();
    }

    @PutMapping("/admin/feedback/surveys/{id}")
    public ResponseEntity<ApiResponseDTO<SurveyResponseDTO>> updateSurvey(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateSurveyRequest request) {
        return notImplemented();
    }

    @GetMapping("/admin/feedback/surveys")
    public ResponseEntity<ApiResponseDTO<PageResponse<SurveyResponseDTO>>> listSurveys() {
        return notImplemented();
    }

    @PostMapping("/feedback/surveys/{id}/responses")
    public ResponseEntity<ApiResponseDTO<SurveyResponseDTO>> submitSurveyResponse(
            @PathVariable("id") String surveyId,
            @Valid @RequestBody SubmitSurveyResponseRequest request) {
        return notImplemented();
    }

    @PostMapping("/feedback/ai-reports")
    public ResponseEntity<ApiResponseDTO<AiReportResponse>> createAiReport(
            @Valid @RequestBody AiReportCreateRequest request) {
        return notImplemented();
    }

    @GetMapping("/admin/feedback/ai-reports")
    public ResponseEntity<ApiResponseDTO<PageResponse<AiReportResponse>>> listAiReports() {
        return notImplemented();
    }

    @GetMapping("/admin/feedback/ai-reports/{id}")
    public ResponseEntity<ApiResponseDTO<AiReportResponse>> getAiReport(@PathVariable("id") String id) {
        return notImplemented();
    }

    private <T> ResponseEntity<ApiResponseDTO<T>> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponseDTO.error(501, "Phase 2 contract only", null));
    }
}
