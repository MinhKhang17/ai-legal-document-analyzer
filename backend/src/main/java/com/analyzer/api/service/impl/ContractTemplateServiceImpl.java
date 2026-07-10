package com.analyzer.api.service.impl;

import com.analyzer.api.dto.contract.ContractTemplateResponse;
import com.analyzer.api.dto.contract.CreateContractTemplateRequest;
import com.analyzer.api.dto.contract.UpdateContractTemplateRequest;
import com.analyzer.api.entity.ContractTemplate;
import com.analyzer.api.enums.ContractTemplateStatus;
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

    private final ContractTemplateRepository templateRepository;

    @Override
    @Transactional
    public ContractTemplateResponse create(CreateContractTemplateRequest request) {
        if (templateRepository.existsByTemplateCode(request.getTemplateCode())) {
            throw new RuntimeException("Template code already exists: " + request.getTemplateCode());
        }

        ContractTemplate template = ContractTemplate.builder()
                .templateCode(request.getTemplateCode())
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .jurisdiction(request.getJurisdiction())
                .content(request.getContent())
                .status(ContractTemplateStatus.ACTIVE)
                .version(1)
                .build();

        ContractTemplate saved = templateRepository.save(template);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ContractTemplateResponse update(Long id, UpdateContractTemplateRequest request) {
        ContractTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract template not found with id: " + id));

        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setCategory(request.getCategory());
        template.setJurisdiction(request.getJurisdiction());
        template.setContent(request.getContent());
        template.setVersion(template.getVersion() + 1);

        ContractTemplate saved = templateRepository.save(template);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContractTemplateResponse> getAll(Pageable pageable) {
        return templateRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractTemplateResponse getById(Long id) {
        ContractTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract template not found with id: " + id));
        return toResponse(template);
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
