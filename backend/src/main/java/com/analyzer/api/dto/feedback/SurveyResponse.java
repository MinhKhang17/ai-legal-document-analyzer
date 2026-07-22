package com.analyzer.api.dto.feedback;

import com.analyzer.api.enums.FeedbackSurveyStatus;
import com.analyzer.api.enums.FeedbackSurveyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyResponse {

    private String id;
    private String code;
    private String title;
    private String description;
    private FeedbackSurveyType surveyType;
    private FeedbackSurveyStatus status;
    private String targetType;
    private Long createdById;
    private Long workspaceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
