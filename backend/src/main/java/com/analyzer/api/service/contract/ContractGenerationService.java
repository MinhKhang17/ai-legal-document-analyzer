package com.analyzer.api.service.contract;

import com.analyzer.api.dto.contract.ContractGenerationResponse;
import com.analyzer.api.dto.contract.GenerateContractRequest;

public interface ContractGenerationService {

    ContractGenerationResponse generate(GenerateContractRequest request);

    ContractGenerationResponse getByRequestId(String requestId);
}
