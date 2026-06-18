package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.customerplan.CustomerPlanResponseDTO;
import com.analyzer.api.dto.customerplan.SubscribeRequestDTO;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.CustomerPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer-plans")
@RequiredArgsConstructor
@Tag(name = "Customer Plan Management", description = "APIs for subscribing and managing customer plans")
public class CustomerPlanController {

    private final CustomerPlanService customerPlanService;

    @PostMapping("/subscribe")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Subscribe to plan", description = "Customer subscribes to a plan and creates a pending transaction.")
    public ResponseEntity<ApiResponseDTO<CustomerPlanResponseDTO>> subscribe(
            @Valid @RequestBody SubscribeRequestDTO request) {
        Long customerId = getCurrentUserId();
        CustomerPlanResponseDTO response = customerPlanService.subscribe(customerId, request);
        return new ResponseEntity<>(ApiResponseDTO.created("Đăng ký gói dịch vụ thành công, vui lòng thanh toán", response), HttpStatus.CREATED);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current plan", description = "Get details of the logged-in customer's active/current plan.")
    public ResponseEntity<ApiResponseDTO<CustomerPlanResponseDTO>> getMyPlan() {
        Long customerId = getCurrentUserId();
        CustomerPlanResponseDTO response = customerPlanService.getMyPlan(customerId);
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy gói dịch vụ hiện tại thành công", response));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel plan", description = "Customer cancels their active subscription plan.")
    public ResponseEntity<ApiResponseDTO<CustomerPlanResponseDTO>> cancelPlan(@PathVariable Long id) {
        Long customerId = getCurrentUserId();
        CustomerPlanResponseDTO response = customerPlanService.cancelPlan(customerId, id);
        return ResponseEntity.ok(ApiResponseDTO.success("Hủy gói dịch vụ thành công", response));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
