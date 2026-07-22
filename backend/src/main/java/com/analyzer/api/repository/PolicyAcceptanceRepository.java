package com.analyzer.api.repository;

import com.analyzer.api.entity.PolicyAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyAcceptanceRepository extends JpaRepository<PolicyAcceptance, String> {
    boolean existsByUserIdAndTermsVersionAndPrivacyPolicyVersion(
            Long userId, String termsVersion, String privacyPolicyVersion);
}
