package com.analyzer.api.service.impl;

import com.analyzer.api.dto.contract.ContractResponse;
import com.analyzer.api.dto.contract.SaveContractRequest;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.ContractStatus;
import com.analyzer.api.enums.SupportedContractType;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.contract.ContractGenerationJobRepository;
import com.analyzer.api.repository.contract.ContractTemplateRepository;
import com.analyzer.api.repository.contract.ContractVersionRepository;
import com.analyzer.api.repository.contract.UserContractRepository;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.contract.UserContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service("legacyUserContractServiceImpl")
@RequiredArgsConstructor
public class UserContractServiceImpl implements UserContractService {

    private final UserContractRepository userContractRepository;
    private final ContractTemplateRepository templateRepository;
    private final ContractGenerationJobRepository generationJobRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ContractVersionRepository contractVersionRepository;

    @Override
    @Transactional
    public ContractResponse save(SaveContractRequest request) {
        Long userId = getCurrentUserId();
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        Workspace workspace = workspaceRepository.findById(request.getWorkspaceId())
                .orElseThrow(() -> new RuntimeException("Workspace not found with id: " + request.getWorkspaceId()));

        ContractTemplate template = null;
        if (request.getTemplateId() != null) {
            template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new RuntimeException("Contract template not found with id: " + request.getTemplateId()));
        }

        ContractGenerationJob generationJob = null;
        if (request.getGenerationJobId() != null && !request.getGenerationJobId().isBlank()) {
            generationJob = generationJobRepository.findById(request.getGenerationJobId())
                    .orElseThrow(() -> new RuntimeException("Generation job not found with id: " + request.getGenerationJobId()));
        }

        Document sourceDocument = null;
        if (request.getSourceDocumentId() != null && !request.getSourceDocumentId().isBlank()) {
            sourceDocument = documentRepository.findById(request.getSourceDocumentId())
                    .orElseThrow(() -> new RuntimeException("Source document not found with id: " + request.getSourceDocumentId()));
        }

        UserContract contract;
        boolean isNew = true;

        if (generationJob != null) {
            Optional<UserContract> existing = userContractRepository.findByGenerationJobId(generationJob.getId());
            if (existing.isPresent()) {
                contract = existing.get();
                isNew = false;
            } else {
                contract = new UserContract();
                contract.setId("contract_" + UUID.randomUUID().toString().replace("-", ""));
            }
        } else {
            contract = new UserContract();
            contract.setId("contract_" + UUID.randomUUID().toString().replace("-", ""));
        }

        String contentHash = calculateHash(request.getContent());

        if (isNew) {
            contract.setOwner(owner);
            contract.setWorkspace(workspace);
            contract.setTemplate(template);
            contract.setGenerationJob(generationJob);
            contract.setSourceDocument(sourceDocument);
            contract.setTitle(request.getTitle());
            contract.setContractType(SupportedContractType.requireSupported(request.getContractType()).name());
            contract.setStatus(ContractStatus.DRAFT);
            contract.setCurrentVersionNo(1);
            contract.setCurrentContentHash(contentHash);
            contract.setLastGeneratedAt(LocalDateTime.now());
            contract = userContractRepository.save(contract);

            // Version 1
            ContractVersion version = ContractVersion.builder()
                    .id("cv_" + UUID.randomUUID().toString().replace("-", ""))
                    .contract(contract)
                    .versionNo(1)
                    .content(request.getContent())
                    .changeSummary("Bản khởi tạo đầu tiên.")
                    .generatedBy(owner)
                    .generatedByAi(generationJob != null)
                    .generationJob(generationJob)
                    .build();

            contractVersionRepository.save(version);
        } else {
            contract.setTitle(request.getTitle());
            contract.setContractType(SupportedContractType.requireSupported(request.getContractType()).name());
            contract.setCurrentVersionNo(contract.getCurrentVersionNo() + 1);
            contract.setCurrentContentHash(contentHash);
            contract.setUpdatedAt(LocalDateTime.now());
            contract = userContractRepository.save(contract);

            // New version
            ContractVersion version = ContractVersion.builder()
                    .id("cv_" + UUID.randomUUID().toString().replace("-", ""))
                    .contract(contract)
                    .versionNo(contract.getCurrentVersionNo())
                    .content(request.getContent())
                    .changeSummary("Cập nhật nội dung hợp đồng.")
                    .generatedBy(owner)
                    .generatedByAi(false)
                    .build();

            contractVersionRepository.save(version);
        }

        return toResponse(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContractResponse> getMyContracts(Long ownerId, Pageable pageable) {
        List<UserContract> list = userContractRepository.findByOwnerId(ownerId);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());
        List<ContractResponse> responses = list.subList(start, end).stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(responses, pageable, list.size());
    }

    @Override
    @Transactional(readOnly = true)
    public ContractResponse getById(Long ownerId, String contractId) {
        UserContract contract = userContractRepository.findByIdAndOwnerId(contractId, ownerId)
                .orElseThrow(() -> new RuntimeException("Contract not found or not owned by user. id: " + contractId));
        return toResponse(contract);
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

    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    private ContractResponse toResponse(UserContract contract) {
        return ContractResponse.builder()
                .id(contract.getId())
                .ownerId(contract.getOwner().getId())
                .workspaceId(contract.getWorkspace().getId())
                .templateId(contract.getTemplate() != null ? contract.getTemplate().getId() : null)
                .generationJobId(contract.getGenerationJob() != null ? contract.getGenerationJob().getId() : null)
                .sourceDocumentId(contract.getSourceDocument() != null ? contract.getSourceDocument().getId() : null)
                .title(contract.getTitle())
                .contractType(contract.getContractType())
                .status(contract.getStatus())
                .currentVersionNo(contract.getCurrentVersionNo())
                .currentContentHash(contract.getCurrentContentHash())
                .lastGeneratedAt(contract.getLastGeneratedAt())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }
}
