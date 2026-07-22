package com.analyzer.api.service.contract.impl;

import com.analyzer.api.dto.contract.ContractResponse;
import com.analyzer.api.dto.contract.SaveContractRequest;
import com.analyzer.api.entity.ContractGenerationJob;
import com.analyzer.api.entity.ContractTemplate;
import com.analyzer.api.entity.ContractVersion;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.UserContract;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.ContractStatus;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.document.DocumentRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.workspace.WorkspaceRepository;
import com.analyzer.api.repository.contract.ContractGenerationJobRepository;
import com.analyzer.api.repository.contract.ContractTemplateRepository;
import com.analyzer.api.repository.contract.ContractVersionRepository;
import com.analyzer.api.repository.contract.UserContractRepository;
import com.analyzer.api.service.contract.UserContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class UserContractServiceImpl implements UserContractService {

    private final UserContractRepository userContractRepository;
    private final ContractVersionRepository versionRepository;
    private final ContractTemplateRepository templateRepository;
    private final ContractGenerationJobRepository jobRepository;
    private final DocumentRepository documentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ContractResponse save(SaveContractRequest request) {
        Long ownerId = CurrentUserSupport.currentUserId();
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay nguoi dung ID: " + ownerId));
        Workspace workspace = workspaceRepository.findById(String.valueOf(request.getWorkspaceId()))
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay workspace ID: " + request.getWorkspaceId()));
        ContractTemplate template = request.getTemplateId() == null ? null : templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay template hop dong ID: " + request.getTemplateId()));
        ContractGenerationJob job = request.getGenerationJobId() == null || request.getGenerationJobId().isBlank()
                ? null
                : jobRepository.findById(request.getGenerationJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay job sinh hop dong ID: " + request.getGenerationJobId()));
        Document sourceDocument = request.getSourceDocumentId() == null || request.getSourceDocumentId().isBlank()
                ? null
                : documentRepository.findById(request.getSourceDocumentId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay document ID: " + request.getSourceDocumentId()));

        UserContract contract = UserContract.builder()
                .id("contract_" + UUID.randomUUID().toString().replace("-", ""))
                .owner(owner)
                .workspace(workspace)
                .template(template)
                .generationJob(job)
                .sourceDocument(sourceDocument)
                .title(request.getTitle().trim())
                .contractType(request.getContractType().trim())
                .status(ContractStatus.DRAFT)
                .currentVersionNo(1)
                .currentContentHash(sha256(request.getContent()))
                .lastGeneratedAt(job == null ? null : LocalDateTime.now())
                .build();

        UserContract savedContract = userContractRepository.save(contract);
        ContractVersion version = ContractVersion.builder()
                .id("cv_" + UUID.randomUUID().toString().replace("-", ""))
                .contract(savedContract)
                .versionNo(1)
                .content(request.getContent())
                .changeSummary("Initial contract version")
                .generatedBy(owner)
                .generatedByAi(job != null)
                .generationJob(job)
                .build();
        versionRepository.save(version);
        return ContractMappingSupport.toContractResponse(savedContract);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContractResponse> getMyContracts(Long ownerId, Pageable pageable) {
        return userContractRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId, pageable)
                .map(ContractMappingSupport::toContractResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractResponse getById(Long ownerId, String contractId) {
        return ContractMappingSupport.toContractResponse(userContractRepository.findByIdAndOwnerId(contractId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay hop dong ID: " + contractId)));
    }

    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(encodedHash.length * 2);
            for (byte b : encodedHash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
