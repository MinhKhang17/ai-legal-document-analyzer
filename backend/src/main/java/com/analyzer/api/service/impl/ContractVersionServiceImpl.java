package com.analyzer.api.service.impl;

import com.analyzer.api.dto.contract.ContractResponse;
import com.analyzer.api.dto.contract.ContractVersionResponse;
import com.analyzer.api.dto.contract.RevertContractVersionRequest;
import com.analyzer.api.entity.ContractVersion;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.UserContract;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.contract.ContractVersionRepository;
import com.analyzer.api.repository.contract.UserContractRepository;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.contract.ContractVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service("legacyContractVersionServiceImpl")
@RequiredArgsConstructor
public class ContractVersionServiceImpl implements ContractVersionService {

    private final ContractVersionRepository contractVersionRepository;
    private final UserContractRepository userContractRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ContractVersionResponse> getVersions(String contractId) {
        return contractVersionRepository.findByContractIdOrderByVersionNoDesc(contractId)
                .stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Override
    @Transactional
    public ContractResponse revert(String contractId, Integer versionNo, RevertContractVersionRequest request) {
        Long userId = getCurrentUserId();
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        UserContract contract = userContractRepository.findByIdAndOwnerId(contractId, userId)
                .orElseThrow(() -> new RuntimeException("Contract not found or not owned by user. id: " + contractId));

        ContractVersion targetVersion = contractVersionRepository.findByContractIdAndVersionNo(contractId, versionNo)
                .orElseThrow(() -> new RuntimeException("Contract version not found: version " + versionNo));

        // Increment version
        int newVersionNo = contract.getCurrentVersionNo() + 1;
        String contentHash = calculateHash(targetVersion.getContent());

        contract.setCurrentVersionNo(newVersionNo);
        contract.setCurrentContentHash(contentHash);
        contract.setUpdatedAt(LocalDateTime.now());
        contract = userContractRepository.save(contract);

        // Save a new version with the target version's content
        ContractVersion newVersion = ContractVersion.builder()
                .id("cv_" + UUID.randomUUID().toString().replace("-", ""))
                .contract(contract)
                .versionNo(newVersionNo)
                .content(targetVersion.getContent())
                .changeSummary("Khôi phục về phiên bản " + versionNo + ". Lý do: " + request.getReason())
                .generatedBy(currentUser)
                .generatedByAi(false)
                .build();

        contractVersionRepository.save(newVersion);

        return toContractResponse(contract);
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

    private ContractVersionResponse toVersionResponse(ContractVersion version) {
        return ContractVersionResponse.builder()
                .id(version.getId())
                .contractId(version.getContract().getId())
                .versionNo(version.getVersionNo())
                .content(version.getContent())
                .changeSummary(version.getChangeSummary())
                .generatedById(version.getGeneratedBy() != null ? version.getGeneratedBy().getId() : null)
                .generatedByAi(version.getGeneratedByAi())
                .generationJobId(version.getGenerationJob() != null ? version.getGenerationJob().getId() : null)
                .createdAt(version.getCreatedAt())
                .build();
    }

    private ContractResponse toContractResponse(UserContract contract) {
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
