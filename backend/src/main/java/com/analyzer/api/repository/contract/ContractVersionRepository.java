package com.analyzer.api.repository.contract;

import com.analyzer.api.entity.ContractVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractVersionRepository extends JpaRepository<ContractVersion, String> {

    List<ContractVersion> findByContractIdOrderByVersionNoDesc(String contractId);

    Optional<ContractVersion> findByContractIdAndVersionNo(String contractId, Integer versionNo);
}
