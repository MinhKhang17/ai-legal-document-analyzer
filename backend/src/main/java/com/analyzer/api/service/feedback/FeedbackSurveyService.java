package com.analyzer.api.service.feedback;

import com.analyzer.api.dto.feedback.CreateSurveyRequest;
import com.analyzer.api.dto.feedback.SurveyResponseDTO;
import com.analyzer.api.dto.feedback.UpdateSurveyRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FeedbackSurveyService {

    SurveyResponseDTO create(CreateSurveyRequest request);

    SurveyResponseDTO update(String id, UpdateSurveyRequest request);

    Page<SurveyResponseDTO> getAll(Pageable pageable);

    SurveyResponseDTO getById(String id);
}
