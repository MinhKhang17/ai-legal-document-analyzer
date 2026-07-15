package com.analyzer.api.service.contract;

import com.analyzer.api.dto.contract.ContractResponse;
import com.analyzer.api.dto.contract.ContractVersionResponse;
import com.analyzer.api.dto.contract.RevertContractVersionRequest;

import java.util.List;

public interface ContractVersionService {

    List<ContractVersionResponse> getVersions(String contractId);

    ContractResponse revert(String contractId, Integer versionNo, RevertContractVersionRequest request);
}
