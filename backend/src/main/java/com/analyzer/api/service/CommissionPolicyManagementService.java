package com.analyzer.api.service;

import com.analyzer.api.dto.revenue.RevenuePayrollDtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CommissionPolicyManagementService {

    BigDecimal rateFor(LocalDate date);

    List<RevenuePayrollDtos.Policy> listPolicies();

    RevenuePayrollDtos.ChangeRequest requestChange(Long adminId, RevenuePayrollDtos.CreateCommissionChange input);

    RevenuePayrollDtos.ChangeRequest resend(Long adminId, String id);

    RevenuePayrollDtos.Policy verify(Long adminId, String requestId, String rawToken);

    RevenuePayrollDtos.Policy cancel(Long adminId, String policyId, String reason);

    void activateDuePolicies();

    List<RevenuePayrollDtos.PolicyNotification> expertNotifications(Long expertId);

    RevenuePayrollDtos.PolicyNotification readNotification(Long expertId, Long id);

    void retryFailed();
}
