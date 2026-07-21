package com.analyzer.api.controller.admin;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.revenue.AdminRevenueOverviewResponse;
import com.analyzer.api.dto.revenue.RevenueSettingResponse;
import com.analyzer.api.dto.revenue.UpdateRevenueSettingRequest;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.ExpertRevenueService;
import com.analyzer.api.service.RevenueSettingService;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.RevenuePayrollDtos;
import com.analyzer.api.service.impl.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/admin/revenue")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Revenue", description = "Endpoints for admins to configure commission rate and view system-wide revenue")
public class AdminRevenueController {

    private final RevenueSettingService revenueSettingService;
    private final ExpertRevenueService expertRevenueService;
    private final RevenuePayrollService payrollService;
    private final CommissionPolicyManagementService commissionService;
    private final EarlyPayoutService earlyPayoutService;
    private final FinancialAuditService financialAuditService;
    private final RevenueWorkbookService workbookService;

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

    @GetMapping("/periods") public ResponseEntity<ApiResponseDTO<PageResponse<RevenuePayrollDtos.Period>>> periods(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok("Revenue periods",payrollService.periods(page,size));}
    @GetMapping("/periods/{id}") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Period>> period(@PathVariable String id){return ok("Revenue period",payrollService.period(id));}
    @GetMapping("/periods/{id}/experts") public ResponseEntity<ApiResponseDTO<java.util.List<RevenuePayrollDtos.Statement>>> experts(@PathVariable String id){return ok("Period statements",payrollService.periodStatements(id));}
    @GetMapping("/statements/{id}") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Statement>> statement(@PathVariable String id){return ok("Revenue statement",payrollService.adminStatement(id));}
    @PostMapping("/periods/{id}/calculate") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Period>> calculate(@PathVariable String id){return ok("Period calculated",payrollService.calculate(id,currentUserId()));}
    @PostMapping("/periods/{id}/close") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Period>> close(@PathVariable String id){return ok("Period closed",payrollService.close(id,currentUserId()));}
    @PostMapping("/periods/{id}/adjustments") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Adjustment>> adjustment(@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.CreateAdjustment request){return ok("Adjustment created",payrollService.addAdjustment(id,currentUserId(),request));}
    @PostMapping("/statements/{id}/payment-pending") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Statement>> statementPending(@PathVariable String id){return ok("Payment pending",payrollService.markRegularPaymentPending(id,currentUserId()));}
    @PostMapping("/statements/{id}/paid") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Statement>> statementPaid(@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.MarkStatementPayment request){return ok("Statement paid",payrollService.markRegularPaid(id,currentUserId(),request));}

    @GetMapping("/commission-policies") public ResponseEntity<ApiResponseDTO<java.util.List<RevenuePayrollDtos.Policy>>> policies(){return ok("Commission policies",commissionService.listPolicies());}
    @PostMapping("/commission-change-requests") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.ChangeRequest>> requestPolicy(@Valid @RequestBody RevenuePayrollDtos.CreateCommissionChange request){return ok("Verification email sent",commissionService.requestChange(currentUserId(),request));}
    @PostMapping("/commission-change-requests/{id}/resend") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.ChangeRequest>> resend(@PathVariable String id){return ok("Verification email resent",commissionService.resend(currentUserId(),id));}
    @PostMapping("/commission-change-requests/{id}/verify") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Policy>> verify(@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.VerifyCommission request){return ok("Commission policy scheduled",commissionService.verify(currentUserId(),id,request.token()));}
    @PostMapping("/commission-policies/{id}/cancel") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Policy>> cancelPolicy(@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.EarlyPayoutNote request){return ok("Commission policy cancelled",commissionService.cancel(currentUserId(),id,request.note()));}

    @GetMapping("/early-payouts") public ResponseEntity<ApiResponseDTO<PageResponse<RevenuePayrollDtos.EarlyPayout>>> earlyPayouts(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok("Early payout requests",earlyPayoutService.adminList(page,size));}
    @GetMapping("/early-payouts/{id}") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> earlyPayout(@PathVariable String id){return ok("Early payout request",earlyPayoutService.adminDetail(id));}
    @PostMapping("/early-payouts/{id}/request-more-info") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> moreInfo(@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.EarlyPayoutNote request){return ok("More information requested",earlyPayoutService.requestMoreInfo(currentUserId(),id,request));}
    @PostMapping("/early-payouts/{id}/approve") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> approve(@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.ApproveEarlyPayout request){return ok("Early payout approved",earlyPayoutService.approve(currentUserId(),id,request));}
    @PostMapping("/early-payouts/{id}/reject") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> reject(@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.RejectEarlyPayout request){return ok("Early payout rejected",earlyPayoutService.reject(currentUserId(),id,request));}
    @PostMapping("/early-payouts/{id}/payment-pending") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> payoutPending(@PathVariable String id){return ok("Payout pending",earlyPayoutService.markPending(currentUserId(),id));}
    @PostMapping("/early-payouts/{id}/paid") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> payoutPaid(@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.MarkPayoutPaid request){return ok("Payout paid",earlyPayoutService.markPaid(currentUserId(),id,request));}
    @GetMapping("/audit") public ResponseEntity<ApiResponseDTO<PageResponse<RevenuePayrollDtos.Audit>>> audit(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return ok("Financial audit",financialAuditService.list(page,size));}
    @GetMapping("/periods/{id}/export") public ResponseEntity<byte[]> export(@PathVariable String id,@RequestParam(required=false) Long expertId){String name="expert-revenue-"+id+(expertId==null?"":"-"+expertId)+".xlsx";return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+URLEncoder.encode(name,StandardCharsets.UTF_8).replace("+","%20")).body(workbookService.adminPeriod(id,expertId));}

    private <T> ResponseEntity<ApiResponseDTO<T>> ok(String message,T data){return ResponseEntity.ok(ApiResponseDTO.success(message,data));}

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) return userDetails.getId();
        throw new IllegalStateException("Invalid authentication principal");
    }
}
