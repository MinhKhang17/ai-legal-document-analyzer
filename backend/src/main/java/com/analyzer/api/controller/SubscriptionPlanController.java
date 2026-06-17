package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanRequestDTO;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanResponseDTO;
import com.analyzer.api.service.SubscriptionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscription-plans")
@RequiredArgsConstructor
@Tag(name = "Subscription Plan Management", description = "APIs for managing subscription plans")
public class SubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create subscription plan", description = "Admin creates a new subscription plan.")
    public ResponseEntity<ApiResponseDTO<SubscriptionPlanResponseDTO>> createPlan(
            @Valid @RequestBody SubscriptionPlanRequestDTO request) {
        SubscriptionPlanResponseDTO response = subscriptionPlanService.createPlan(request);
        return new ResponseEntity<>(ApiResponseDTO.created("Tạo gói dịch vụ thành công", response), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active subscription plans", description = "Get list of all active subscription plans.")
    public ResponseEntity<ApiResponseDTO<List<SubscriptionPlanResponseDTO>>> getActivePlans() {
        List<SubscriptionPlanResponseDTO> response = subscriptionPlanService.getActivePlans();
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy danh sách gói dịch vụ thành công", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription plan by ID", description = "Retrieves details of a subscription plan by its ID.")
    public ResponseEntity<ApiResponseDTO<SubscriptionPlanResponseDTO>> getPlanById(@PathVariable Long id) {
        SubscriptionPlanResponseDTO response = subscriptionPlanService.getPlanById(id);
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy thông tin gói dịch vụ thành công", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update subscription plan", description = "Admin updates an existing subscription plan.")
    public ResponseEntity<ApiResponseDTO<SubscriptionPlanResponseDTO>> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionPlanRequestDTO request) {
        SubscriptionPlanResponseDTO response = subscriptionPlanService.updatePlan(id, request);
        return ResponseEntity.ok(ApiResponseDTO.success("Cập nhật gói dịch vụ thành công", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft delete subscription plan", description = "Admin soft deletes a subscription plan by setting active to false.")
    public ResponseEntity<ApiResponseDTO<Void>> deletePlan(@PathVariable Long id) {
        subscriptionPlanService.deletePlan(id);
        return ResponseEntity.ok(ApiResponseDTO.success("Xóa gói dịch vụ thành công"));
    }
}
