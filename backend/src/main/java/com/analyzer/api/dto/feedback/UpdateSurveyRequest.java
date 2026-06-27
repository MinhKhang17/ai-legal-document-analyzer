package com.analyzer.api.dto.feedback;

import com.analyzer.api.enums.FeedbackSurveyStatus;
import com.analyzer.api.enums.FeedbackSurveyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSurveyRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 2000)
    private String description;

    @NotNull
    private FeedbackSurveyType surveyType;

    @NotBlank
    private String targetType;

    @NotNull
    private FeedbackSurveyStatus status;
}
