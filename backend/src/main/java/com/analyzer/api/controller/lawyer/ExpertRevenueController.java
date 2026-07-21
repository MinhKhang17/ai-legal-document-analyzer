package com.analyzer.api.controller.lawyer;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.ExpertRevenueSummaryResponse;
import com.analyzer.api.dto.revenue.ExpertRevenueTicketResponse;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.ExpertRevenueService;
import com.analyzer.api.dto.revenue.RevenuePayrollDtos;
import com.analyzer.api.service.impl.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/expert/revenue")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EXPERT')")
public class ExpertRevenueController {
    private final ExpertRevenueService revenueService;
    private final RevenuePayrollService payrollService;
    private final CommissionPolicyManagementService commissionService;
    private final EarlyPayoutService earlyPayoutService;
    private final RevenueWorkbookService workbookService;

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

    @GetMapping("/periods") public ResponseEntity<ApiResponseDTO<PageResponse<RevenuePayrollDtos.Statement>>> periods(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok("Revenue statements",payrollService.expertStatements(currentUserId(),page,size));}
    @GetMapping("/periods/{statementId}") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Statement>> period(@PathVariable String statementId){return ok("Revenue statement",payrollService.expertStatement(currentUserId(),statementId));}
    @GetMapping("/commission-policies") public ResponseEntity<ApiResponseDTO<java.util.List<RevenuePayrollDtos.Policy>>> policies(){return ok("Commission policies",commissionService.listPolicies());}
    @GetMapping("/commission-notifications") public ResponseEntity<ApiResponseDTO<java.util.List<RevenuePayrollDtos.PolicyNotification>>> notifications(){return ok("Commission notifications",commissionService.expertNotifications(currentUserId()));}
    @PostMapping("/commission-notifications/{id}/read") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.PolicyNotification>> read(@PathVariable Long id){return ok("Notification read",commissionService.readNotification(currentUserId(),id));}
    @PostMapping("/early-payouts") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> createEarly(@Valid @RequestBody RevenuePayrollDtos.CreateEarlyPayout request){return ok("Early payout requested",earlyPayoutService.create(currentUserId(),request));}
    @GetMapping("/early-payouts") public ResponseEntity<ApiResponseDTO<PageResponse<RevenuePayrollDtos.EarlyPayout>>> earlyList(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok("Early payout requests",earlyPayoutService.expertList(currentUserId(),page,size));}
    @GetMapping("/early-payouts/{id}") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> earlyDetail(@PathVariable String id){return ok("Early payout request",earlyPayoutService.expertDetail(currentUserId(),id));}
    @PostMapping("/early-payouts/{id}/cancel") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> cancel(@PathVariable String id){return ok("Early payout cancelled",earlyPayoutService.cancel(currentUserId(),id));}
    @PostMapping("/early-payouts/{id}/reply") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> reply(@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.EarlyPayoutNote request){return ok("Early payout response sent",earlyPayoutService.reply(currentUserId(),id,request));}
    @GetMapping("/periods/{statementId}/export") public ResponseEntity<byte[]> export(@PathVariable String statementId){String name="expert-revenue-"+statementId+".xlsx";return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+URLEncoder.encode(name,StandardCharsets.UTF_8).replace("+","%20")).body(workbookService.expertStatement(currentUserId(),statementId));}

    private <T> ResponseEntity<ApiResponseDTO<T>> ok(String message,T data){return ResponseEntity.ok(ApiResponseDTO.success(message,data));}

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) return userDetails.getId();
        throw new IllegalStateException("Invalid authentication principal");
    }
}
