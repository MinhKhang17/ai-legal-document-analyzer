package com.analyzer.api.controller.lawyer;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.ExpertRevenueSummaryResponse;
import com.analyzer.api.dto.revenue.ExpertRevenueTicketResponse;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.ExpertRevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/expert/revenue")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EXPERT')")
public class ExpertRevenueController {
    private final ExpertRevenueService revenueService;

    @GetMapping
    public ResponseEntity<ApiResponseDTO<ExpertRevenueSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponseDTO.success("Lay tong quan doanh thu thanh cong",
                revenueService.getSummary(currentUserId())));
    }

    @GetMapping("/tickets")
    public ResponseEntity<ApiResponseDTO<PageResponse<ExpertRevenueTicketResponse>>> tickets(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponseDTO.success("Lay doanh thu theo ticket thanh cong",
                revenueService.getTickets(currentUserId(), page, size)));
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) return userDetails.getId();
        throw new IllegalStateException("Invalid authentication principal");
    }
}
