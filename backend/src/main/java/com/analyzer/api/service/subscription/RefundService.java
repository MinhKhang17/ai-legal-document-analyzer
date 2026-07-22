package com.analyzer.api.service.subscription;

import com.analyzer.api.dto.subscription.CreateRefundRequest;
import com.analyzer.api.dto.subscription.RefundResponse;
import com.analyzer.api.dto.subscription.UpdateRefundStatusRequest;
import com.analyzer.api.enums.RefundStatus;

import java.util.List;

public interface RefundService {

    RefundResponse requestRefund(Long customerId, CreateRefundRequest request);

    RefundResponse getRefund(Long refundId);

    List<RefundResponse> getMyRefunds(Long customerId);

    List<RefundResponse> getRefunds(RefundStatus status);

    RefundResponse updateRefundStatus(Long refundId, UpdateRefundStatusRequest request);

    RefundResponse confirmRefundEmail(String token);
}
