package com.analyzer.api.repository.feedback;

import com.analyzer.api.entity.FeedbackSurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackSurveyResponseRepository extends JpaRepository<FeedbackSurveyResponse, String> {

    List<FeedbackSurveyResponse> findBySurveyIdOrderByCreatedAtDesc(String surveyId);
}
