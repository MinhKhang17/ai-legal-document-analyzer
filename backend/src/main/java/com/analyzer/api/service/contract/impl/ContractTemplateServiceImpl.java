package com.analyzer.api.service.contract.impl;

import com.analyzer.api.dto.contract.ContractTemplateResponse;
import com.analyzer.api.dto.contract.CreateContractTemplateRequest;
import com.analyzer.api.dto.contract.UpdateContractTemplateRequest;
import com.analyzer.api.entity.ContractTemplate;
import com.analyzer.api.enums.ContractTemplateStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.contract.ContractTemplateRepository;
import com.analyzer.api.service.contract.ContractTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContractTemplateServiceImpl implements ContractTemplateService {

    private final ContractTemplateRepository contractTemplateRepository;

    @Override
    @Transactional
    public ContractTemplateResponse create(CreateContractTemplateRequest request) {
        contractTemplateRepository.findByTemplateCode(request.getTemplateCode().trim())
                .ifPresent(template -> {
                    throw new ConflictException("Ma template hop dong da ton tai: " + template.getTemplateCode());
                });

        ContractTemplate template = ContractTemplate.builder()
                .templateCode(request.getTemplateCode().trim())
                .name(request.getName().trim())
                .description(request.getDescription())
                .category(request.getCategory().trim())
                .jurisdiction(request.getJurisdiction())
                .content(request.getContent())
                .status(ContractTemplateStatus.ACTIVE)
                .version(1)
                .build();
        return toResponse(contractTemplateRepository.save(template));
    }

    @Override
    @Transactional
    public ContractTemplateResponse update(Long id, UpdateContractTemplateRequest request) {
        ContractTemplate template = contractTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay template hop dong ID: " + id));

        template.setName(request.getName().trim());
        template.setDescription(request.getDescription());
        template.setCategory(request.getCategory().trim());
        template.setJurisdiction(request.getJurisdiction());
        template.setContent(request.getContent());
        template.setVersion((template.getVersion() == null ? 1 : template.getVersion()) + 1);
        return toResponse(contractTemplateRepository.save(template));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContractTemplateResponse> getAll(Pageable pageable) {
        return contractTemplateRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractTemplateResponse getById(Long id) {
        return toResponse(contractTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay template hop dong ID: " + id)));
    }

    private ContractTemplateResponse toResponse(ContractTemplate template) {
        return ContractTemplateResponse.builder()
                .id(template.getId())
                .templateCode(template.getTemplateCode())
                .name(template.getName())
                .description(template.getDescription())
                .category(template.getCategory())
                .jurisdiction(template.getJurisdiction())
                .content(template.getContent())
                .status(template.getStatus())
                .version(template.getVersion())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
