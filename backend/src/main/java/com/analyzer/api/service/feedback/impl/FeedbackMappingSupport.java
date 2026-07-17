package com.analyzer.api.service.feedback.impl;

import com.analyzer.api.dto.feedback.AiReportResponse;
import com.analyzer.api.dto.feedback.SurveyResponseDTO;
import com.analyzer.api.entity.AiReport;
import com.analyzer.api.entity.FeedbackSurvey;

final class FeedbackMappingSupport {

    private FeedbackMappingSupport() {
    }

    static SurveyResponseDTO toSurveyResponse(FeedbackSurvey survey) {
        return SurveyResponseDTO.builder()
                .id(survey.getId())
                .code(survey.getCode())
                .title(survey.getTitle())
                .description(survey.getDescription())
                .surveyType(survey.getSurveyType())
                .status(survey.getStatus())
                .targetType(survey.getTargetType())
                .createdById(survey.getCreatedBy().getId())
                .workspaceId(toLongOrNull(survey.getWorkspace() == null ? null : survey.getWorkspace().getId()))
                .createdAt(survey.getCreatedAt())
                .updatedAt(survey.getUpdatedAt())
                .build();
    }

    static AiReportResponse toAiReportResponse(AiReport report) {
        return AiReportResponse.builder()
                .id(report.getId())
                .reportType(report.getReportType())
                .sourceType(report.getSourceType())
                .sourceReferenceId(report.getSourceReferenceId())
                .summary(report.getSummary())
                .detailsJson(report.getDetailsJson())
                .submittedById(report.getSubmittedBy() == null ? null : report.getSubmittedBy().getId())
                .workspaceId(toLongOrNull(report.getWorkspace() == null ? null : report.getWorkspace().getId()))
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }

    private static Long toLongOrNull(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
