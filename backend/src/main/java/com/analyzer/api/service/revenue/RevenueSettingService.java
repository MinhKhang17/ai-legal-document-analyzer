package com.analyzer.api.service.revenue;

import com.analyzer.api.dto.revenue.RevenueSettingResponse;
import com.analyzer.api.dto.revenue.UpdateRevenueSettingRequest;
import java.math.BigDecimal;

public interface RevenueSettingService {
    BigDecimal getCurrentRate();
    RevenueSettingResponse getSetting();
    RevenueSettingResponse updateRate(Long adminId, UpdateRevenueSettingRequest request);
}
