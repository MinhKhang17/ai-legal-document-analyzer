package com.analyzer.api.service.contract;

import com.analyzer.api.dto.contract.ContractResponse;
import com.analyzer.api.dto.contract.SaveContractRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserContractService {

    ContractResponse save(SaveContractRequest request);

    Page<ContractResponse> getMyContracts(Long ownerId, Pageable pageable);

    ContractResponse getById(Long ownerId, String contractId);
}
