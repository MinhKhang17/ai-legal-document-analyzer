package com.analyzer.api.service.contract.impl;

import com.analyzer.api.dto.contract.ContractResponse;
import com.analyzer.api.dto.contract.ContractVersionResponse;
import com.analyzer.api.dto.contract.RevertContractVersionRequest;
import com.analyzer.api.entity.ContractVersion;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.UserContract;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.contract.ContractVersionRepository;
import com.analyzer.api.repository.contract.UserContractRepository;
import com.analyzer.api.service.contract.ContractVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class ContractVersionServiceImpl implements ContractVersionService {

    private final UserContractRepository userContractRepository;
    private final ContractVersionRepository versionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ContractVersionResponse> getVersions(String contractId) {
        Long ownerId = CurrentUserSupport.currentUserId();
        userContractRepository.findByIdAndOwnerId(contractId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay hop dong ID: " + contractId));
        return versionRepository.findByContractIdOrderByVersionNoDesc(contractId)
                .stream()
                .map(ContractMappingSupport::toVersionResponse)
                .toList();
    }

    @Override
    @Transactional
    public ContractResponse revert(String contractId, Integer versionNo, RevertContractVersionRequest request) {
        Long ownerId = CurrentUserSupport.currentUserId();
        UserContract contract = userContractRepository.findByIdAndOwnerId(contractId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay hop dong ID: " + contractId));
        ContractVersion target = versionRepository.findByContractIdAndVersionNo(contractId, versionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phien ban hop dong: " + versionNo));
        User user = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay nguoi dung ID: " + ownerId));

        int nextVersionNo = (contract.getCurrentVersionNo() == null ? 1 : contract.getCurrentVersionNo()) + 1;
        ContractVersion reverted = ContractVersion.builder()
                .id("cv_" + UUID.randomUUID().toString().replace("-", ""))
                .contract(contract)
                .versionNo(nextVersionNo)
                .content(target.getContent())
                .changeSummary("Revert to version " + versionNo + ": " + request.getReason())
                .generatedBy(user)
                .generatedByAi(false)
                .generationJob(target.getGenerationJob())
                .build();
        versionRepository.save(reverted);

        contract.setCurrentVersionNo(nextVersionNo);
        contract.setCurrentContentHash(UserContractServiceImpl.sha256(target.getContent()));
        return ContractMappingSupport.toContractResponse(userContractRepository.save(contract));
    }
}
