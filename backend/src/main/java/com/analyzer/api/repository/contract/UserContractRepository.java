package com.analyzer.api.repository.contract;

import com.analyzer.api.entity.UserContract;
import com.analyzer.api.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserContractRepository extends JpaRepository<UserContract, String> {

    List<UserContract> findByOwnerId(Long ownerId);

    Optional<UserContract> findByIdAndOwnerId(String id, Long ownerId);

    List<UserContract> findByStatus(ContractStatus status);
}
