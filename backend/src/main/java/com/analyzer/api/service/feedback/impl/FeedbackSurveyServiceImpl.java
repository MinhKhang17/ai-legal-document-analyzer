package com.analyzer.api.service.feedback.impl;

import com.analyzer.api.dto.feedback.CreateSurveyRequest;
import com.analyzer.api.dto.feedback.SurveyResponseDTO;
import com.analyzer.api.dto.feedback.UpdateSurveyRequest;
import com.analyzer.api.entity.FeedbackSurvey;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.FeedbackSurveyStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.repository.feedback.FeedbackSurveyRepository;
import com.analyzer.api.service.feedback.FeedbackSurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackSurveyServiceImpl implements FeedbackSurveyService {

    private final FeedbackSurveyRepository surveyRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    @Override
    @Transactional
    public SurveyResponseDTO create(CreateSurveyRequest request) {
        surveyRepository.findByCode(request.getCode().trim())
                .ifPresent(survey -> {
                    throw new ConflictException("Ma survey da ton tai: " + survey.getCode());
                });
        User createdBy = userRepository.findById(request.getCreatedById())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay nguoi tao ID: " + request.getCreatedById()));
        Workspace workspace = request.getWorkspaceId() == null ? null : workspaceRepository.findById(String.valueOf(request.getWorkspaceId()))
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay workspace ID: " + request.getWorkspaceId()));

        FeedbackSurvey survey = FeedbackSurvey.builder()
                .id("fs_" + UUID.randomUUID().toString().replace("-", ""))
                .code(request.getCode().trim())
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .surveyType(request.getSurveyType())
                .status(FeedbackSurveyStatus.DRAFT)
                .targetType(request.getTargetType().trim())
                .createdBy(createdBy)
                .workspace(workspace)
                .build();
        return FeedbackMappingSupport.toSurveyResponse(surveyRepository.save(survey));
    }

    @Override
    @Transactional
    public SurveyResponseDTO update(String id, UpdateSurveyRequest request) {
        FeedbackSurvey survey = surveyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay survey ID: " + id));
        survey.setTitle(request.getTitle().trim());
        survey.setDescription(request.getDescription());
        survey.setSurveyType(request.getSurveyType());
        survey.setTargetType(request.getTargetType().trim());
        survey.setStatus(request.getStatus());
        return FeedbackMappingSupport.toSurveyResponse(surveyRepository.save(survey));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SurveyResponseDTO> getAll(Pageable pageable) {
        return surveyRepository.findAll(pageable).map(FeedbackMappingSupport::toSurveyResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyResponseDTO getById(String id) {
        return FeedbackMappingSupport.toSurveyResponse(surveyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay survey ID: " + id)));
    }
}
