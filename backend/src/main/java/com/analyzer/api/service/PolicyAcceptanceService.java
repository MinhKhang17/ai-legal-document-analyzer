package com.analyzer.api.service;

import com.analyzer.api.dto.policy.PolicyAcceptanceDtos.StatusResponse;

public interface PolicyAcceptanceService {
    StatusResponse currentStatus(Long userId);
    StatusResponse acceptCurrent(Long userId, String remoteAddress, String userAgent);
    void requireCurrent(Long userId);
    String currentTermsVersion();
    String currentPrivacyVersion();
}
