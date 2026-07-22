package com.analyzer.api.controller.feedback;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.feedback.*;
import com.analyzer.api.service.feedback.AiReportService;
import com.analyzer.api.service.feedback.FeedbackSurveyResponseService;
import com.analyzer.api.service.feedback.FeedbackSurveyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackSurveyService feedbackSurveyService;
    private final FeedbackSurveyResponseService feedbackSurveyResponseService;
    private final AiReportService aiReportService;

    @PostMapping("/admin/feedback/surveys")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<SurveyResponse>> createSurvey(
            @Valid @RequestBody CreateSurveyRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponseDTO.created("Tao survey feedback thanh cong", feedbackSurveyService.create(request)));
    }

    @PutMapping("/admin/feedback/surveys/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<SurveyResponse>> updateSurvey(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateSurveyRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Cap nhat survey feedback thanh cong",
                feedbackSurveyService.update(id, request)));
    }

    @GetMapping("/admin/feedback/surveys")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<PageResponse<SurveyResponse>>> listSurveys(Pageable pageable) {
        Page<SurveyResponse> page = feedbackSurveyService.getAll(pageable);
        return ResponseEntity.ok(ApiResponseDTO.success("Lay danh sach survey feedback thanh cong", toPageResponse(page)));
    }

    @PostMapping("/feedback/surveys/{id}/responses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<SurveyResponse>> submitSurveyResponse(
            @PathVariable("id") String surveyId,
            @Valid @RequestBody SubmitSurveyResponseRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponseDTO.created("Gui phan hoi survey thanh cong",
                        feedbackSurveyResponseService.submit(surveyId, request)));
    }

    @PostMapping("/feedback/ai-reports")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<AiReportResponse>> createAiReport(
            @Valid @RequestBody AiReportCreateRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponseDTO.created("Gui bao cao AI thanh cong", aiReportService.create(request)));
    }

    @GetMapping("/admin/feedback/ai-reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<PageResponse<AiReportResponse>>> listAiReports(Pageable pageable) {
        Page<AiReportResponse> page = aiReportService.getAll(pageable);
        return ResponseEntity.ok(ApiResponseDTO.success("Lay danh sach bao cao AI thanh cong", toPageResponse(page)));
    }

    @GetMapping("/admin/feedback/ai-reports/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<AiReportResponse>> getAiReport(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponseDTO.success("Lay bao cao AI thanh cong", aiReportService.getById(id)));
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return PageResponse.<T>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
