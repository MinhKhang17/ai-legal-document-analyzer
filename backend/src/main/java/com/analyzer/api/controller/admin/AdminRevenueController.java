package com.analyzer.api.controller.admin;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.revenue.AdminRevenueOverviewResponse;
import com.analyzer.api.dto.revenue.RevenueSettingResponse;
import com.analyzer.api.dto.revenue.UpdateRevenueSettingRequest;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.ExpertRevenueService;
import com.analyzer.api.service.RevenueSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/revenue")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Revenue", description = "Endpoints for admins to configure commission rate and view system-wide revenue")
public class AdminRevenueController {

    private final RevenueSettingService revenueSettingService;
    private final ExpertRevenueService expertRevenueService;

    @GetMapping("/settings")
    @Operation(summary = "View current commission rate setting")
    public ResponseEntity<ApiResponseDTO<RevenueSettingResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved revenue setting successfully",
                revenueSettingService.getSetting()));
    }

    @PatchMapping("/settings")
    @Operation(summary = "Update commission rate setting")
    public ResponseEntity<ApiResponseDTO<RevenueSettingResponse>> updateSettings(
            @Valid @RequestBody UpdateRevenueSettingRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Updated revenue setting successfully",
                revenueSettingService.updateRate(currentUserId(), request)));
    }

    @GetMapping("/overview")
    @Operation(summary = "View system-wide expert revenue overview")
    public ResponseEntity<ApiResponseDTO<AdminRevenueOverviewResponse>> getOverview() {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved revenue overview successfully",
                expertRevenueService.getAdminOverview()));
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) return userDetails.getId();
        throw new IllegalStateException("Invalid authentication principal");
    }
}
