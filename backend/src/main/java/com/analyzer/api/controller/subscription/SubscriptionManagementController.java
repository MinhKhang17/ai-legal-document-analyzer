package com.analyzer.api.controller.subscription;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.subscription.RefundRequestDTO;
import com.analyzer.api.dto.subscription.RefundResponseDTO;
import com.analyzer.api.dto.subscription.SubscriptionUsageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionManagementController {

    @GetMapping("/my-usage")
    public ResponseEntity<ApiResponseDTO<PageResponse<SubscriptionUsageResponse>>> getMyUsage() {
        return notImplemented();
    }

    @PostMapping("/refunds")
    public ResponseEntity<ApiResponseDTO<RefundResponseDTO>> requestRefund(
            @Valid @RequestBody RefundRequestDTO request) {
        return notImplemented();
    }

    @GetMapping("/refunds/{id}")
    public ResponseEntity<ApiResponseDTO<RefundResponseDTO>> getRefund(@PathVariable("id") Long refundId) {
        return notImplemented();
    }

    @GetMapping("/refunds")
    public ResponseEntity<ApiResponseDTO<List<RefundResponseDTO>>> listRefunds() {
        return notImplemented();
    }

    private <T> ResponseEntity<ApiResponseDTO<T>> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponseDTO.error(501, "Phase 2 contract only", null));
    }
}
