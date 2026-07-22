package com.analyzer.api.service.policy.impl;

import com.analyzer.api.dto.policy.PolicyAcceptanceDtos.StatusResponse;
import com.analyzer.api.entity.PolicyAcceptance;
import com.analyzer.api.entity.User;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.policy.PolicyAcceptanceRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.service.policy.PolicyAcceptanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class PolicyAcceptanceServiceImpl implements PolicyAcceptanceService {
    private final PolicyAcceptanceRepository repository;
    private final UserRepository userRepository;
    @Value("${app.policy.terms-version:2026-07-22}") private String termsVersion;
    @Value("${app.policy.privacy-version:2026-07-22}") private String privacyVersion;

    @Override @Transactional(readOnly = true)
    public StatusResponse currentStatus(Long userId) {
        boolean accepted = repository.existsByUserIdAndTermsVersionAndPrivacyPolicyVersion(
                userId, termsVersion, privacyVersion);
        return new StatusResponse(termsVersion, privacyVersion, accepted, null);
    }

    @Override @Transactional
    public StatusResponse acceptCurrent(Long userId, String remoteAddress, String userAgent) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));
        if (!repository.existsByUserIdAndTermsVersionAndPrivacyPolicyVersion(userId, termsVersion, privacyVersion)) {
            LocalDateTime now = LocalDateTime.now();
            repository.save(PolicyAcceptance.builder()
                    .id("pac_" + UUID.randomUUID().toString().replace("-", ""))
                    .user(user).termsVersion(termsVersion).privacyPolicyVersion(privacyVersion)
                    .acceptedAt(now).ipHash(hash(remoteAddress)).userAgentHash(hash(userAgent)).build());
            return new StatusResponse(termsVersion, privacyVersion, true, now);
        }
        return new StatusResponse(termsVersion, privacyVersion, true, null);
    }

    @Override @Transactional(readOnly = true)
    public void requireCurrent(Long userId) {
        if (!repository.existsByUserIdAndTermsVersionAndPrivacyPolicyVersion(userId, termsVersion, privacyVersion)) {
            throw new ForbiddenException("TERMS_NOT_ACCEPTED", "Accept the current Terms of Use and Data Processing Policy first");
        }
    }

    @Override public String currentTermsVersion() { return termsVersion; }
    @Override public String currentPrivacyVersion() { return privacyVersion; }

    private String hash(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Cannot create policy audit hash", ex); }
    }
}
