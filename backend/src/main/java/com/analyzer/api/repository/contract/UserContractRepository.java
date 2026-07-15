package com.analyzer.api.repository.contract;

import com.analyzer.api.entity.UserContract;
import com.analyzer.api.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserContractRepository extends JpaRepository<UserContract, String> {

    List<UserContract> findByOwnerId(Long ownerId);

    Page<UserContract> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId, Pageable pageable);

    Optional<UserContract> findByIdAndOwnerId(String id, Long ownerId);

    Optional<UserContract> findByGenerationJobId(String generationJobId);

    List<UserContract> findByStatus(ContractStatus status);
}
