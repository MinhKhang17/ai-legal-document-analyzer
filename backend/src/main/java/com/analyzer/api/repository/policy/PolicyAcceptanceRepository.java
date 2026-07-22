package com.analyzer.api.repository.policy;

import com.analyzer.api.entity.PolicyAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicyAcceptanceRepository extends JpaRepository<PolicyAcceptance, String> {
    boolean existsByUserIdAndTermsVersionAndPrivacyPolicyVersion(
            Long userId, String termsVersion, String privacyPolicyVersion);
}
