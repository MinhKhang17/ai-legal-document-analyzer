package com.analyzer.api.dto.feedback;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitSurveyResponseRequest {

    @NotNull
    private Long respondentId;

    private String sourceReferenceId;

    private Integer rating;

    private String answerJson;

    private String comment;
}
