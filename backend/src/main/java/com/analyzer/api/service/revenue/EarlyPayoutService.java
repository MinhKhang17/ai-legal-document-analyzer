package com.analyzer.api.service.revenue;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.RevenuePayrollDtos;

public interface EarlyPayoutService {

    RevenuePayrollDtos.EarlyPayout create(Long expertId, RevenuePayrollDtos.CreateEarlyPayout input);

    PageResponse<RevenuePayrollDtos.EarlyPayout> expertList(Long id, int page, int size);

    RevenuePayrollDtos.EarlyPayout expertDetail(Long expertId, String id);

    PageResponse<RevenuePayrollDtos.EarlyPayout> adminList(int page, int size);

    RevenuePayrollDtos.EarlyPayout adminDetail(String id);

    RevenuePayrollDtos.EarlyPayout cancel(Long expertId, String id);

    RevenuePayrollDtos.EarlyPayout reply(Long expertId, String id, RevenuePayrollDtos.EarlyPayoutNote input);

    RevenuePayrollDtos.EarlyPayout requestMoreInfo(Long adminId, String id, RevenuePayrollDtos.EarlyPayoutNote input);

    RevenuePayrollDtos.EarlyPayout approve(Long adminId, String id, RevenuePayrollDtos.ApproveEarlyPayout input);

    RevenuePayrollDtos.EarlyPayout reject(Long adminId, String id, RevenuePayrollDtos.RejectEarlyPayout input);

    RevenuePayrollDtos.EarlyPayout markPending(Long adminId, String id);

    RevenuePayrollDtos.EarlyPayout markPaid(Long adminId, String id, RevenuePayrollDtos.MarkPayoutPaid input);
}
