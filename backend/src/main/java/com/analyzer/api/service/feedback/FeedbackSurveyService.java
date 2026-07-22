package com.analyzer.api.service.feedback;

import com.analyzer.api.dto.feedback.CreateSurveyRequest;
import com.analyzer.api.dto.feedback.SurveyResponse;
import com.analyzer.api.dto.feedback.UpdateSurveyRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FeedbackSurveyService {

    SurveyResponse create(CreateSurveyRequest request);

    SurveyResponse update(String id, UpdateSurveyRequest request);

    Page<SurveyResponse> getAll(Pageable pageable);

    SurveyResponse getById(String id);
}
