package com.analyzer.api.repository.feedback;

import com.analyzer.api.entity.FeedbackSurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackSurveyResponseRepository extends JpaRepository<FeedbackSurveyResponse, String> {

    List<FeedbackSurveyResponse> findBySurveyIdOrderByCreatedAtDesc(String surveyId);
}
