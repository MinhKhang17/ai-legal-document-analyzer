package com.analyzer.api.service.impl;

import com.analyzer.api.dto.revenue.UpdateRevenueSettingRequest;
import com.analyzer.api.entity.RevenueSetting;
import com.analyzer.api.entity.User;
import com.analyzer.api.repository.revenue.RevenueSettingRepository;
import com.analyzer.api.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueSettingServiceImplTest {
    @Mock RevenueSettingRepository revenueSettingRepository;
    @Mock UserRepository userRepository;

    private RevenueSettingServiceImpl service() {
        return new RevenueSettingServiceImpl(revenueSettingRepository, userRepository);
    }

    @Test
    void getCurrentRateFallsBackToDefaultWhenNotSeeded() {
        when(revenueSettingRepository.findById(1L)).thenReturn(Optional.empty());

        assertEquals(new BigDecimal("0.2000"), service().getCurrentRate());
    }

    @Test
    void getCurrentRateReturnsStoredValue() {
        RevenueSetting setting = RevenueSetting.builder().id(1L).commissionRate(new BigDecimal("0.1500")).build();
        when(revenueSettingRepository.findById(1L)).thenReturn(Optional.of(setting));

        assertEquals(new BigDecimal("0.1500"), service().getCurrentRate());
    }

    @Test
    void updateRateCreatesRowWhenMissingAndTracksActor() {
        User admin = User.builder().id(1L).firstName("Ad").lastName("Min").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(revenueSettingRepository.findById(1L)).thenReturn(Optional.empty());
        when(revenueSettingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateRevenueSettingRequest request = new UpdateRevenueSettingRequest();
        request.setCommissionRate(new BigDecimal("0.3000"));

        var response = service().updateRate(1L, request);

        assertEquals(new BigDecimal("0.3000"), response.getCommissionRate());
        assertEquals("Ad Min", response.getUpdatedByName());
    }
}
