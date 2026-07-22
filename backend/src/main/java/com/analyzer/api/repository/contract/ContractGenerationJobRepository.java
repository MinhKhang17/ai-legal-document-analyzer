package com.analyzer.api.repository.contract;

import com.analyzer.api.entity.ContractGenerationJob;
import com.analyzer.api.enums.ContractGenerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractGenerationJobRepository extends JpaRepository<ContractGenerationJob, String> {

    Optional<ContractGenerationJob> findByRequestId(String requestId);

    List<ContractGenerationJob> findByStatus(ContractGenerationStatus status);

    long countByRequesterIdAndStatusAndCreatedAtBetween(
            Long requesterId,
            ContractGenerationStatus status,
            LocalDateTime start,
            LocalDateTime end);
}
