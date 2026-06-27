package com.analyzer.api.repository.contract;

import com.analyzer.api.entity.ContractTemplate;
import com.analyzer.api.enums.ContractTemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {

    Optional<ContractTemplate> findByTemplateCode(String templateCode);

    List<ContractTemplate> findByStatus(ContractTemplateStatus status);
}
