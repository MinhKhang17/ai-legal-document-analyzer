package com.analyzer.api.service.feedback;

import com.analyzer.api.dto.feedback.SubmitSurveyResponseRequest;
import com.analyzer.api.dto.feedback.SurveyResponse;

public interface FeedbackSurveyResponseService {

    SurveyResponse submit(String surveyId, SubmitSurveyResponseRequest request);
}
