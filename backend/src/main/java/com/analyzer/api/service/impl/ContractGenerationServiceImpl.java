package com.analyzer.api.service.impl;

import com.analyzer.api.dto.contract.ContractGenerationResponse;
import com.analyzer.api.dto.contract.GenerateContractRequest;
import com.analyzer.api.dto.ai.GenerateContractApiRequest;
import com.analyzer.api.dto.ai.GenerateContractApiResponse;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.ContractGenerationStatus;
import com.analyzer.api.enums.ContractStatus;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.contract.ContractGenerationJobRepository;
import com.analyzer.api.repository.contract.ContractTemplateRepository;
import com.analyzer.api.repository.contract.ContractVersionRepository;
import com.analyzer.api.repository.contract.UserContractRepository;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.AiClient;
import com.analyzer.api.service.SubscriptionQuotaService;
import com.analyzer.api.service.contract.ContractGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class ContractGenerationServiceImpl implements ContractGenerationService {

    private final ContractGenerationJobRepository generationJobRepository;
    private final ContractTemplateRepository templateRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserContractRepository userContractRepository;
    private final ContractVersionRepository contractVersionRepository;
    private final AiClient aiClient;
    private final SubscriptionQuotaService subscriptionQuotaService;

    @Override
    @Transactional
    public ContractGenerationResponse generate(GenerateContractRequest request) {
        // Idempotency check
        return generationJobRepository.findByRequestId(request.getRequestId())
                .map(this::toResponse)
                .orElseGet(() -> executeGeneration(request));
    }

    @Override
    @Transactional(readOnly = true)
    public ContractGenerationResponse getByRequestId(String requestId) {
        ContractGenerationJob job = generationJobRepository.findByRequestId(requestId)
                .orElseThrow(() -> new RuntimeException("Contract generation job not found with request id: " + requestId));
        return toResponse(job);
    }

    private ContractGenerationResponse executeGeneration(GenerateContractRequest request) {
        Long userId = getCurrentUserId();
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        subscriptionQuotaService.checkCanDraftContract(requester);

        Workspace workspace = workspaceRepository.findById(request.getWorkspaceId())
                .orElseThrow(() -> new RuntimeException("Workspace not found with id: " + request.getWorkspaceId()));

        ContractTemplate template = null;
        if (request.getTemplateId() != null) {
            template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new RuntimeException("Contract template not found with id: " + request.getTemplateId()));
        }

        Document sourceDocument = null;
        if (request.getSourceDocumentId() != null) {
            sourceDocument = documentRepository.findById(request.getSourceDocumentId())
                    .orElseThrow(() -> new RuntimeException("Source document not found with id: " + request.getSourceDocumentId()));
        }

        String jobId = "cg_" + UUID.randomUUID().toString().replace("-", "");
        ContractGenerationJob job = ContractGenerationJob.builder()
                .id(jobId)
                .requestId(request.getRequestId())
                .requester(requester)
                .workspace(workspace)
                .template(template)
                .sourceDocument(sourceDocument)
                .inputJson(request.getInputJson())
                .status(ContractGenerationStatus.PROCESSING)
                .build();

        // Save initial job state
        job = generationJobRepository.save(job);

        try {
            // Call Python AI Service
            GenerateContractApiRequest apiRequest = GenerateContractApiRequest.builder()
                    .requestId(request.getRequestId())
                    .templateContent(template != null ? template.getContent() : null)
                    .inputJson(request.getInputJson())
                    .build();

            GenerateContractApiResponse apiResponse = aiClient.generateContract(apiRequest);

            if (apiResponse.getError() != null && !apiResponse.getError().isBlank()) {
                job.setStatus(ContractGenerationStatus.FAILED);
                job.setErrorMessage(apiResponse.getError());
                job.setPromptSnapshot(apiResponse.getPromptSnapshot());
                generationJobRepository.save(job);
                return toResponse(job);
            }

            job.setStatus(ContractGenerationStatus.COMPLETED);
            job.setPromptSnapshot(apiResponse.getPromptSnapshot());
            job.setOutputDraft(apiResponse.getOutputDraft());
            job = generationJobRepository.save(job);

            // Create UserContract
            String contractId = "contract_" + UUID.randomUUID().toString().replace("-", "");
            String title = template != null ? "Hợp đồng - " + template.getName() : "Hợp đồng thuê nhà";
            String contractType = template != null ? template.getCategory() : "LEASE";

            UserContract contract = UserContract.builder()
                    .id(contractId)
                    .owner(requester)
                    .workspace(workspace)
                    .template(template)
                    .generationJob(job)
                    .sourceDocument(sourceDocument)
                    .title(title)
                    .contractType(contractType)
                    .status(ContractStatus.GENERATED)
                    .currentVersionNo(1)
                    .lastGeneratedAt(LocalDateTime.now())
                    .build();

            contract = userContractRepository.save(contract);

            // Create ContractVersion
            String versionId = "cv_" + UUID.randomUUID().toString().replace("-", "");
            ContractVersion version = ContractVersion.builder()
                    .id(versionId)
                    .contract(contract)
                    .versionNo(1)
                    .content(apiResponse.getOutputDraft())
                    .changeSummary("Bản nháp được tạo tự động bởi AI.")
                    .generatedBy(requester)
                    .generatedByAi(true)
                    .generationJob(job)
                    .build();

            contractVersionRepository.save(version);
            subscriptionQuotaService.recordDraftContractUsage(requester);

        } catch (Exception e) {
            job.setStatus(ContractGenerationStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            generationJobRepository.save(job);
        }

        return toResponse(job);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }

        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }

    private ContractGenerationResponse toResponse(ContractGenerationJob job) {
        return ContractGenerationResponse.builder()
                .id(job.getId())
                .requestId(job.getRequestId())
                .requesterId(job.getRequester().getId())
                .workspaceId(job.getWorkspace().getId())
                .templateId(job.getTemplate() != null ? job.getTemplate().getId() : null)
                .sourceDocumentId(job.getSourceDocument() != null ? job.getSourceDocument().getId() : null)
                .inputJson(job.getInputJson())
                .promptSnapshot(job.getPromptSnapshot())
                .outputDraft(job.getOutputDraft())
                .status(job.getStatus())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
