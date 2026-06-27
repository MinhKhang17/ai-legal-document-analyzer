package com.analyzer.api.service.feedback;

import com.analyzer.api.dto.feedback.SubmitSurveyResponseRequest;
import com.analyzer.api.dto.feedback.SurveyResponseDTO;

public interface FeedbackSurveyResponseService {

    SurveyResponseDTO submit(String surveyId, SubmitSurveyResponseRequest request);
}
