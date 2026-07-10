package com.analyzer.api.service.feedback.impl;

import com.analyzer.api.dto.feedback.SubmitSurveyResponseRequest;
import com.analyzer.api.dto.feedback.SurveyResponseDTO;
import com.analyzer.api.entity.FeedbackSurvey;
import com.analyzer.api.entity.FeedbackSurveyResponse;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.FeedbackSurveyStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.feedback.FeedbackSurveyRepository;
import com.analyzer.api.repository.feedback.FeedbackSurveyResponseRepository;
import com.analyzer.api.service.feedback.FeedbackSurveyResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackSurveyResponseServiceImpl implements FeedbackSurveyResponseService {

    private final FeedbackSurveyRepository surveyRepository;
    private final FeedbackSurveyResponseRepository responseRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public SurveyResponseDTO submit(String surveyId, SubmitSurveyResponseRequest request) {
        FeedbackSurvey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay survey ID: " + surveyId));
        if (survey.getStatus() != FeedbackSurveyStatus.ACTIVE) {
            throw new ConflictException("Survey khong o trang thai ACTIVE");
        }
        User respondent = userRepository.findById(request.getRespondentId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay nguoi phan hoi ID: " + request.getRespondentId()));
        FeedbackSurveyResponse response = FeedbackSurveyResponse.builder()
                .id("fsr_" + UUID.randomUUID().toString().replace("-", ""))
                .survey(survey)
                .respondent(respondent)
                .sourceReferenceId(request.getSourceReferenceId())
                .rating(request.getRating())
                .answerJson(request.getAnswerJson())
                .comment(request.getComment())
                .build();
        responseRepository.save(response);
        return FeedbackMappingSupport.toSurveyResponse(survey);
    }
}
