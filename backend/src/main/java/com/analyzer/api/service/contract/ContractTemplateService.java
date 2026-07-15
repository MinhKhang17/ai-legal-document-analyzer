package com.analyzer.api.service.contract;

import com.analyzer.api.dto.contract.ContractTemplateResponse;
import com.analyzer.api.dto.contract.CreateContractTemplateRequest;
import com.analyzer.api.dto.contract.UpdateContractTemplateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContractTemplateService {

    ContractTemplateResponse create(CreateContractTemplateRequest request);

    ContractTemplateResponse update(Long id, UpdateContractTemplateRequest request);

    Page<ContractTemplateResponse> getAll(Pageable pageable);

    ContractTemplateResponse getById(Long id);
}
