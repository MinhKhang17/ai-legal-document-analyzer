package com.analyzer.api.service.feedback.impl;

import com.analyzer.api.dto.feedback.AiReportCreateRequest;
import com.analyzer.api.dto.feedback.AiReportResponse;
import com.analyzer.api.entity.AiReport;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.AiReportStatus;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.repository.feedback.AiReportRepository;
import com.analyzer.api.service.feedback.AiReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiReportServiceImpl implements AiReportService {

    private final AiReportRepository aiReportRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    @Override
    @Transactional
    public AiReportResponse create(AiReportCreateRequest request) {
        User submittedBy = userRepository.findById(request.getSubmittedById())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay nguoi bao cao ID: " + request.getSubmittedById()));
        Workspace workspace = request.getWorkspaceId() == null ? null : workspaceRepository.findById(String.valueOf(request.getWorkspaceId()))
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay workspace ID: " + request.getWorkspaceId()));
        AiReport report = AiReport.builder()
                .id("air_" + UUID.randomUUID().toString().replace("-", ""))
                .reportType(request.getReportType().trim())
                .sourceType(request.getSourceType().trim())
                .sourceReferenceId(request.getSourceReferenceId().trim())
                .summary(request.getSummary().trim())
                .detailsJson(request.getDetailsJson())
                .submittedBy(submittedBy)
                .workspace(workspace)
                .status(AiReportStatus.OPEN)
                .build();
        return FeedbackMappingSupport.toAiReportResponse(aiReportRepository.save(report));
    }

    @Override
    @Transactional(readOnly = true)
    public AiReportResponse getById(String id) {
        return FeedbackMappingSupport.toAiReportResponse(aiReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay AI report ID: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AiReportResponse> getAll(Pageable pageable) {
        return aiReportRepository.findAll(pageable).map(FeedbackMappingSupport::toAiReportResponse);
    }
}
