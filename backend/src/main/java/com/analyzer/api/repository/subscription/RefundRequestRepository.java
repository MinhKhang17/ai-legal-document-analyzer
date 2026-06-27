package com.analyzer.api.repository.subscription;

import com.analyzer.api.entity.RefundRequest;
import com.analyzer.api.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    List<RefundRequest> findByStatus(RefundStatus status);
}
