package com.analyzer.api.service.contract.impl;

import com.analyzer.api.dto.contract.ContractGenerationResponse;
import com.analyzer.api.dto.contract.ContractResponse;
import com.analyzer.api.dto.contract.ContractVersionResponse;
import com.analyzer.api.entity.ContractGenerationJob;
import com.analyzer.api.entity.ContractVersion;
import com.analyzer.api.entity.UserContract;

final class ContractMappingSupport {

    private ContractMappingSupport() {
    }

    static ContractGenerationResponse toGenerationResponse(ContractGenerationJob job) {
        return ContractGenerationResponse.builder()
                .id(job.getId())
                .requestId(job.getRequestId())
                .requesterId(job.getRequester().getId())
                .workspaceId(toLongOrNull(job.getWorkspace().getId()))
                .templateId(job.getTemplate() == null ? null : job.getTemplate().getId())
                .sourceDocumentId(job.getSourceDocument() == null ? null : job.getSourceDocument().getId())
                .inputJson(job.getInputJson())
                .promptSnapshot(job.getPromptSnapshot())
                .outputDraft(job.getOutputDraft())
                .status(job.getStatus())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    static ContractResponse toContractResponse(UserContract contract) {
        return ContractResponse.builder()
                .id(contract.getId())
                .ownerId(contract.getOwner().getId())
                .workspaceId(toLongOrNull(contract.getWorkspace().getId()))
                .templateId(contract.getTemplate() == null ? null : contract.getTemplate().getId())
                .generationJobId(contract.getGenerationJob() == null ? null : contract.getGenerationJob().getId())
                .sourceDocumentId(contract.getSourceDocument() == null ? null : contract.getSourceDocument().getId())
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

    static ContractVersionResponse toVersionResponse(ContractVersion version) {
        return ContractVersionResponse.builder()
                .id(version.getId())
                .contractId(version.getContract().getId())
                .versionNo(version.getVersionNo())
                .content(version.getContent())
                .changeSummary(version.getChangeSummary())
                .generatedById(version.getGeneratedBy() == null ? null : version.getGeneratedBy().getId())
                .generatedByAi(version.getGeneratedByAi())
                .generationJobId(version.getGenerationJob() == null ? null : version.getGenerationJob().getId())
                .createdAt(version.getCreatedAt())
                .build();
    }

    private static Long toLongOrNull(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
