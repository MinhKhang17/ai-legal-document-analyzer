package com.analyzer.api.controller.subscription;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.customerplan.CustomerPlanResponseDTO;
import com.analyzer.api.dto.customerplan.SubscribeRequestDTO;
import com.analyzer.api.dto.subscription.RefundRequestDTO;
import com.analyzer.api.dto.subscription.RefundResponseDTO;
import com.analyzer.api.dto.subscription.SubscriptionUsageResponse;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanRequestDTO;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanResponseDTO;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.CustomerPlanService;
import com.analyzer.api.service.SubscriptionPlanService;
import com.analyzer.api.service.subscription.RefundService;
import com.analyzer.api.service.subscription.SubscriptionUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription & Usage Management", description = "Unified APIs for plans, customer subscriptions, usage tracking, and refunds")
public class SubscriptionManagementController {

    private final SubscriptionPlanService subscriptionPlanService;
    private final CustomerPlanService customerPlanService;
    private final SubscriptionUsageService subscriptionUsageService;
    private final RefundService refundService;

    // --- Subscription Plan Endpoints ---

    @GetMapping("/plans")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active subscription plans")
    public ResponseEntity<ApiResponseDTO<List<SubscriptionPlanResponseDTO>>> getActivePlans() {
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy danh sách gói dịch vụ thành công",
                subscriptionPlanService.getActivePlans()));
    }

    @PostMapping("/plans")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create subscription plan (Admin)")
    public ResponseEntity<ApiResponseDTO<SubscriptionPlanResponseDTO>> createPlan(
            @Valid @RequestBody SubscriptionPlanRequestDTO request) {
        return new ResponseEntity<>(ApiResponseDTO.created("Tạo gói dịch vụ thành công",
                subscriptionPlanService.createPlan(request)), HttpStatus.CREATED);
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription plan by ID")
    public ResponseEntity<ApiResponseDTO<SubscriptionPlanResponseDTO>> getPlanById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy thông tin gói dịch vụ thành công",
                subscriptionPlanService.getPlanById(id)));
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update subscription plan (Admin)")
    public ResponseEntity<ApiResponseDTO<SubscriptionPlanResponseDTO>> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionPlanRequestDTO request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Cập nhật gói dịch vụ thành công",
                subscriptionPlanService.updatePlan(id, request)));
    }

    @DeleteMapping("/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft delete subscription plan (Admin)")
    public ResponseEntity<ApiResponseDTO<Void>> deletePlan(@PathVariable Long id) {
        subscriptionPlanService.deletePlan(id);
        return ResponseEntity.ok(ApiResponseDTO.success("Xóa gói dịch vụ thành công"));
    }

    // --- Customer Subscription Endpoints ---

    @PostMapping("/subscribe")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Subscribe to plan")
    public ResponseEntity<ApiResponseDTO<CustomerPlanResponseDTO>> subscribe(
            @Valid @RequestBody SubscribeRequestDTO request) {
        Long customerId = getCurrentUserId();
        return new ResponseEntity<>(ApiResponseDTO.created("Đăng ký gói dịch vụ thành công",
                customerPlanService.subscribe(customerId, request)), HttpStatus.CREATED);
    }

    @GetMapping("/my-plan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my active plan")
    public ResponseEntity<ApiResponseDTO<CustomerPlanResponseDTO>> getMyPlan() {
        Long customerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy gói dịch vụ hiện tại thành công",
                customerPlanService.getMyPlan(customerId)));
    }

    @PutMapping("/my-plan/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel active plan")
    public ResponseEntity<ApiResponseDTO<CustomerPlanResponseDTO>> cancelPlan(@PathVariable Long id) {
        Long customerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Hủy gói dịch vụ thành công",
                customerPlanService.cancelPlan(customerId, id)));
    }

    // --- Usage & Refund Endpoints ---

    @GetMapping("/my-usage")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my usage history")
    public ResponseEntity<ApiResponseDTO<PageResponse<SubscriptionUsageResponse>>> getMyUsage(Pageable pageable) {
        Long customerId = getCurrentUserId();
        Page<SubscriptionUsageResponse> pageResult = subscriptionUsageService.getMyUsage(customerId, pageable);
        PageResponse<SubscriptionUsageResponse> response = PageResponse.<SubscriptionUsageResponse>builder()
                .items(pageResult.getContent())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalItems(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy lịch sử sử dụng thành công", response));
    }

    @PostMapping("/refunds")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Request refund for transaction")
    public ResponseEntity<ApiResponseDTO<RefundResponseDTO>> requestRefund(
            @Valid @RequestBody RefundRequestDTO request) {
        Long customerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Gửi yêu cầu hoàn tiền thành công",
                refundService.requestRefund(customerId, request)));
    }

    @GetMapping("/refunds/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get refund request detail")
    public ResponseEntity<ApiResponseDTO<RefundResponseDTO>> getRefund(@PathVariable("id") Long refundId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy thông tin hoàn tiền thành công",
                refundService.getRefund(refundId)));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }
        if (authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
