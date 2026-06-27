package com.analyzer.api.service.subscription;

import com.analyzer.api.dto.subscription.RefundRequestDTO;
import com.analyzer.api.dto.subscription.RefundResponseDTO;

public interface RefundService {

    RefundResponseDTO requestRefund(Long customerId, RefundRequestDTO request);

    RefundResponseDTO getRefund(Long refundId);
}
