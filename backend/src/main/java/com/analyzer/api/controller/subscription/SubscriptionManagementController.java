package com.analyzer.api.controller.subscription;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.customerplan.CustomerPlanResponseDTO;
import com.analyzer.api.dto.customerplan.SubscribeRequestDTO;
import com.analyzer.api.dto.subscription.RefundRequestDTO;
import com.analyzer.api.dto.subscription.RefundResponseDTO;
import com.analyzer.api.dto.subscription.SubscriptionQuotaUsageSummaryResponse;
import com.analyzer.api.dto.subscription.SubscriptionUsageResponse;
import com.analyzer.api.dto.subscription.UpdateRefundStatusRequest;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanRequestDTO;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanResponseDTO;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.RefundStatus;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.CustomerPlanService;
import com.analyzer.api.service.SubscriptionPlanService;
import com.analyzer.api.service.SubscriptionQuotaService;
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
@RequestMapping({"/api/v1/subscriptions", "/api/subscription"})
@RequiredArgsConstructor
@Tag(name = "Subscription & Usage Management", description = "Unified APIs for plans, customer subscriptions, usage tracking, and refunds")
public class SubscriptionManagementController {

    private final SubscriptionPlanService subscriptionPlanService;
    private final CustomerPlanService customerPlanService;
    private final SubscriptionUsageService subscriptionUsageService;
    private final RefundService refundService;
    private final SubscriptionQuotaService subscriptionQuotaService;

    @GetMapping("/plans")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active subscription plans")
    public ResponseEntity<ApiResponseDTO<List<SubscriptionPlanResponseDTO>>> getActivePlans() {
        return ResponseEntity.ok(ApiResponseDTO.success("Lay danh sach goi dich vu thanh cong",
                subscriptionPlanService.getActivePlans()));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my current subscription plan")
    public ResponseEntity<ApiResponseDTO<SubscriptionPlanResponseDTO>> getCurrentPlan() {
        User user = User.builder().id(getCurrentUserId()).build();
        SubscriptionPlanResponseDTO response = subscriptionPlanService.getPlanById(
                subscriptionQuotaService.getCurrentPlan(user).getId());
        return ResponseEntity.ok(ApiResponseDTO.success("Lay goi hien tai thanh cong", response));
    }

    @GetMapping("/usage")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my current monthly usage summary")
    public ResponseEntity<ApiResponseDTO<SubscriptionQuotaUsageSummaryResponse>> getCurrentUsage() {
        User user = User.builder().id(getCurrentUserId()).build();
        return ResponseEntity.ok(ApiResponseDTO.success("Lay thong tin usage thanh cong",
                subscriptionQuotaService.getCurrentUsage(user)));
    }

    @PostMapping("/change-plan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change plan")
    public ResponseEntity<ApiResponseDTO<CustomerPlanResponseDTO>> changePlan(
            @Valid @RequestBody SubscribeRequestDTO request) {
        Long customerId = getCurrentUserId();
        return new ResponseEntity<>(ApiResponseDTO.created("Thay doi goi dich vu thanh cong",
                customerPlanService.subscribe(customerId, request)), HttpStatus.CREATED);
    }

    @PostMapping("/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<SubscriptionPlanResponseDTO>> createPlan(
            @Valid @RequestBody SubscriptionPlanRequestDTO request) {
        return new ResponseEntity<>(ApiResponseDTO.created("Tao goi dich vu thanh cong",
                subscriptionPlanService.createPlan(request)), HttpStatus.CREATED);
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<SubscriptionPlanResponseDTO>> getPlanById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDTO.success("Lay thong tin goi dich vu thanh cong",
                subscriptionPlanService.getPlanById(id)));
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<SubscriptionPlanResponseDTO>> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionPlanRequestDTO request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Cap nhat goi dich vu thanh cong",
                subscriptionPlanService.updatePlan(id, request)));
    }

    @DeleteMapping("/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<Void>> deletePlan(@PathVariable Long id) {
        subscriptionPlanService.deletePlan(id);
        return ResponseEntity.ok(ApiResponseDTO.success("Xoa goi dich vu thanh cong"));
    }

    @PostMapping("/subscribe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<CustomerPlanResponseDTO>> subscribe(
            @Valid @RequestBody SubscribeRequestDTO request) {
        Long customerId = getCurrentUserId();
        return new ResponseEntity<>(ApiResponseDTO.created("Dang ky goi dich vu thanh cong",
                customerPlanService.subscribe(customerId, request)), HttpStatus.CREATED);
    }

    @GetMapping("/my-plan")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<CustomerPlanResponseDTO>> getMyPlan() {
        Long customerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Lay goi dang su dung thanh cong",
                customerPlanService.getMyPlan(customerId)));
    }

    @PutMapping("/my-plan/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<CustomerPlanResponseDTO>> cancelPlan(@PathVariable Long id) {
        Long customerId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponseDTO.success("Huy goi dich vu thanh cong",
                customerPlanService.cancelPlan(customerId, id)));
    }

    @GetMapping("/my-usage")
    @PreAuthorize("isAuthenticated()")
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
        return ResponseEntity.ok(ApiResponseDTO.success("Lay lich su su dung thanh cong", response));
    }

    @PostMapping("/refunds")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<RefundResponseDTO>> requestRefund(
            @Valid @RequestBody RefundRequestDTO request) {
        Long customerId = getCurrentUserId();
        return new ResponseEntity<>(ApiResponseDTO.created("Gui yeu cau hoan tien thanh cong",
                refundService.requestRefund(customerId, request)), HttpStatus.CREATED);
    }

    @GetMapping("/refunds/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my refund requests")
    public ResponseEntity<ApiResponseDTO<List<RefundResponseDTO>>> getMyRefunds() {
        return ResponseEntity.ok(ApiResponseDTO.success("Lay danh sach hoan tien thanh cong",
                refundService.getMyRefunds(getCurrentUserId())));
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get refund requests for administration")
    public ResponseEntity<ApiResponseDTO<List<RefundResponseDTO>>> getRefunds(
            @RequestParam(required = false) RefundStatus status) {
        return ResponseEntity.ok(ApiResponseDTO.success("Lay danh sach hoan tien thanh cong",
                refundService.getRefunds(status)));
    }

    @GetMapping("/refunds/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<RefundResponseDTO>> getRefund(@PathVariable("id") Long refundId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Lay thong tin hoan tien thanh cong",
                refundService.getRefund(refundId)));
    }

    @PatchMapping("/refunds/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve, reject, process, or complete a refund request")
    public ResponseEntity<ApiResponseDTO<RefundResponseDTO>> updateRefundStatus(
            @PathVariable("id") Long refundId,
            @Valid @RequestBody UpdateRefundStatusRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Cap nhat trang thai hoan tien thanh cong",
                refundService.updateRefundStatus(refundId, request)));
    }

    @GetMapping("/refunds/confirm")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Confirm a refund request from the email link")
    public ResponseEntity<ApiResponseDTO<RefundResponseDTO>> confirmRefundEmail(@RequestParam String token) {
        return ResponseEntity.ok(ApiResponseDTO.success("CONFIRMATION_SUCCESS",
                refundService.confirmRefundEmail(token)));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Ban chua dang nhap");
        }
        if (authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thong tin xac thuc khong hop le");
    }
}
