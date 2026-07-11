package com.analyzer.api.service.contract.impl;

import com.analyzer.api.dto.contract.ContractGenerationResponse;
import com.analyzer.api.dto.contract.GenerateContractRequest;
import com.analyzer.api.entity.ContractGenerationJob;
import com.analyzer.api.entity.ContractTemplate;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.ContractGenerationStatus;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.repository.contract.ContractGenerationJobRepository;
import com.analyzer.api.repository.contract.ContractTemplateRepository;
import com.analyzer.api.service.contract.ContractGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("legacyContractGenerationServiceImpl")
@RequiredArgsConstructor
public class ContractGenerationServiceImpl implements ContractGenerationService {

    private final ContractGenerationJobRepository jobRepository;
    private final ContractTemplateRepository templateRepository;
    private final DocumentRepository documentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ContractGenerationResponse generate(GenerateContractRequest request) {
        return jobRepository.findByRequestId(request.getRequestId())
                .map(ContractMappingSupport::toGenerationResponse)
                .orElseGet(() -> createGenerationJob(request));
    }

    @Override
    @Transactional(readOnly = true)
    public ContractGenerationResponse getByRequestId(String requestId) {
        return ContractMappingSupport.toGenerationResponse(jobRepository.findByRequestId(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay job sinh hop dong: " + requestId)));
    }

    private ContractGenerationResponse createGenerationJob(GenerateContractRequest request) {
        Long userId = CurrentUserSupport.currentUserId();
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay nguoi dung ID: " + userId));
        Workspace workspace = workspaceRepository.findById(String.valueOf(request.getWorkspaceId()))
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay workspace ID: " + request.getWorkspaceId()));
        ContractTemplate template = request.getTemplateId() == null ? null : templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay template hop dong ID: " + request.getTemplateId()));
        Document sourceDocument = request.getSourceDocumentId() == null || request.getSourceDocumentId().isBlank()
                ? null
                : documentRepository.findById(request.getSourceDocumentId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay document ID: " + request.getSourceDocumentId()));

        String outputDraft = template == null ? request.getInputJson() : template.getContent();
        ContractGenerationJob job = ContractGenerationJob.builder()
                .id("cg_" + UUID.randomUUID().toString().replace("-", ""))
                .requestId(request.getRequestId().trim())
                .requester(requester)
                .workspace(workspace)
                .template(template)
                .sourceDocument(sourceDocument)
                .inputJson(request.getInputJson())
                .promptSnapshot(template == null ? null : template.getContent())
                .outputDraft(outputDraft)
                .status(ContractGenerationStatus.COMPLETED)
                .build();
        return ContractMappingSupport.toGenerationResponse(jobRepository.save(job));
    }
}
