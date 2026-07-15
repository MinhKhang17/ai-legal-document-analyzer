package com.analyzer.api.repository.feedback;

import com.analyzer.api.entity.FeedbackSurvey;
import com.analyzer.api.enums.FeedbackSurveyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackSurveyRepository extends JpaRepository<FeedbackSurvey, String> {

    Optional<FeedbackSurvey> findByCode(String code);

    List<FeedbackSurvey> findByStatus(FeedbackSurveyStatus status);
}
