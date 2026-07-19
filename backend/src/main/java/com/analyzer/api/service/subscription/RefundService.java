package com.analyzer.api.service.subscription;

import com.analyzer.api.dto.subscription.RefundRequestDTO;
import com.analyzer.api.dto.subscription.RefundResponseDTO;
import com.analyzer.api.dto.subscription.UpdateRefundStatusRequest;
import com.analyzer.api.enums.RefundStatus;

import java.util.List;

public interface RefundService {

    RefundResponseDTO requestRefund(Long customerId, RefundRequestDTO request);

    RefundResponseDTO getRefund(Long refundId);

    List<RefundResponseDTO> getMyRefunds(Long customerId);

    List<RefundResponseDTO> getRefunds(RefundStatus status);

    RefundResponseDTO updateRefundStatus(Long refundId, UpdateRefundStatusRequest request);

    RefundResponseDTO confirmRefundEmail(String token);
}
