package com.analyzer.api.service.impl;

import com.analyzer.api.dto.revenue.RevenueSettingResponse;
import com.analyzer.api.dto.revenue.UpdateRevenueSettingRequest;
import com.analyzer.api.entity.RevenueSetting;
import com.analyzer.api.entity.User;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.RevenueSettingRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.RevenueSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RevenueSettingServiceImpl implements RevenueSettingService {

    private static final Long SETTING_ID = 1L;
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.2000");

    private final RevenueSettingRepository revenueSettingRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getCurrentRate() {
        return revenueSettingRepository.findById(SETTING_ID)
                .map(RevenueSetting::getCommissionRate)
                .orElse(DEFAULT_COMMISSION_RATE);
    }

    @Override
    @Transactional(readOnly = true)
    public RevenueSettingResponse getSetting() {
        return revenueSettingRepository.findById(SETTING_ID)
                .map(this::toResponse)
                .orElseGet(() -> RevenueSettingResponse.builder()
                        .commissionRate(DEFAULT_COMMISSION_RATE)
                        .build());
    }

    @Override
    @Transactional
    public RevenueSettingResponse updateRate(Long adminId, UpdateRevenueSettingRequest request) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));

        RevenueSetting setting = revenueSettingRepository.findById(SETTING_ID)
                .orElse(RevenueSetting.builder().id(SETTING_ID).build());
        setting.setCommissionRate(request.getCommissionRate());
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(admin);

        return toResponse(revenueSettingRepository.save(setting));
    }

    private RevenueSettingResponse toResponse(RevenueSetting setting) {
        return RevenueSettingResponse.builder()
                .commissionRate(setting.getCommissionRate())
                .updatedAt(setting.getUpdatedAt())
                .updatedByName(setting.getUpdatedBy() != null
                        ? (setting.getUpdatedBy().getFirstName() + " " + setting.getUpdatedBy().getLastName()).trim()
                        : null)
                .build();
    }
}
