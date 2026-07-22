package com.analyzer.api.controller.admin;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.revenue.AdminRevenueOverviewResponse;
import com.analyzer.api.dto.revenue.RevenueSettingResponse;
import com.analyzer.api.dto.revenue.UpdateRevenueSettingRequest;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.revenue.ExpertRevenueService;
import com.analyzer.api.service.revenue.RevenueSettingService;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.RevenuePayrollDtos;
import com.analyzer.api.service.revenue.CommissionPolicyManagementService;
import com.analyzer.api.service.revenue.EarlyPayoutService;
import com.analyzer.api.service.revenue.FinancialAuditService;
import com.analyzer.api.service.revenue.RevenuePayrollService;
import com.analyzer.api.service.revenue.RevenueWorkbookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody UpdateRevenueSettingRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Updated revenue setting successfully",
                revenueSettingService.updateRate(currentUser.getId(), request)));
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
    @PostMapping("/periods/{id}/calculate") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Period>> calculate(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id){return ok("Period calculated",payrollService.calculate(id,currentUser.getId()));}
    @PostMapping("/periods/{id}/close") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Period>> close(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id){return ok("Period closed",payrollService.close(id,currentUser.getId()));}
    @PostMapping("/periods/{id}/adjustments") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Adjustment>> adjustment(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.CreateAdjustment request){return ok("Adjustment created",payrollService.addAdjustment(id,currentUser.getId(),request));}
    @PostMapping("/statements/{id}/payment-pending") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Statement>> statementPending(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id){return ok("Payment pending",payrollService.markRegularPaymentPending(id,currentUser.getId()));}
    @PostMapping("/statements/{id}/paid") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Statement>> statementPaid(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.MarkStatementPayment request){return ok("Statement paid",payrollService.markRegularPaid(id,currentUser.getId(),request));}

    @GetMapping("/commission-policies") public ResponseEntity<ApiResponseDTO<java.util.List<RevenuePayrollDtos.Policy>>> policies(){return ok("Commission policies",commissionService.listPolicies());}
    @PostMapping("/commission-change-requests") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.ChangeRequest>> requestPolicy(@AuthenticationPrincipal UserDetailsImpl currentUser,@Valid @RequestBody RevenuePayrollDtos.CreateCommissionChange request){return ok("Verification email sent",commissionService.requestChange(currentUser.getId(),request));}
    @PostMapping("/commission-change-requests/{id}/resend") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.ChangeRequest>> resend(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id){return ok("Verification email resent",commissionService.resend(currentUser.getId(),id));}
    @PostMapping("/commission-change-requests/{id}/verify") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Policy>> verify(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.VerifyCommission request){return ok("Commission policy scheduled",commissionService.verify(currentUser.getId(),id,request.token()));}
    @PostMapping("/commission-policies/{id}/cancel") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.Policy>> cancelPolicy(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.EarlyPayoutNote request){return ok("Commission policy cancelled",commissionService.cancel(currentUser.getId(),id,request.note()));}

    @GetMapping("/early-payouts") public ResponseEntity<ApiResponseDTO<PageResponse<RevenuePayrollDtos.EarlyPayout>>> earlyPayouts(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok("Early payout requests",earlyPayoutService.adminList(page,size));}
    @GetMapping("/early-payouts/{id}") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> earlyPayout(@PathVariable String id){return ok("Early payout request",earlyPayoutService.adminDetail(id));}
    @PostMapping("/early-payouts/{id}/request-more-info") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> moreInfo(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.EarlyPayoutNote request){return ok("More information requested",earlyPayoutService.requestMoreInfo(currentUser.getId(),id,request));}
    @PostMapping("/early-payouts/{id}/approve") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> approve(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.ApproveEarlyPayout request){return ok("Early payout approved",earlyPayoutService.approve(currentUser.getId(),id,request));}
    @PostMapping("/early-payouts/{id}/reject") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> reject(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.RejectEarlyPayout request){return ok("Early payout rejected",earlyPayoutService.reject(currentUser.getId(),id,request));}
    @PostMapping("/early-payouts/{id}/payment-pending") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> payoutPending(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id){return ok("Payout pending",earlyPayoutService.markPending(currentUser.getId(),id));}
    @PostMapping("/early-payouts/{id}/paid") public ResponseEntity<ApiResponseDTO<RevenuePayrollDtos.EarlyPayout>> payoutPaid(@AuthenticationPrincipal UserDetailsImpl currentUser,@PathVariable String id,@Valid @RequestBody RevenuePayrollDtos.MarkPayoutPaid request){return ok("Payout paid",earlyPayoutService.markPaid(currentUser.getId(),id,request));}
    @GetMapping("/audit") public ResponseEntity<ApiResponseDTO<PageResponse<RevenuePayrollDtos.Audit>>> audit(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return ok("Financial audit",financialAuditService.list(page,size));}
    @GetMapping("/periods/{id}/export") public ResponseEntity<byte[]> export(@PathVariable String id,@RequestParam(required=false) Long expertId){String name="expert-revenue-"+id+(expertId==null?"":"-"+expertId)+".xlsx";return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+URLEncoder.encode(name,StandardCharsets.UTF_8).replace("+","%20")).body(workbookService.adminPeriod(id,expertId));}

    private <T> ResponseEntity<ApiResponseDTO<T>> ok(String message,T data){return ResponseEntity.ok(ApiResponseDTO.success(message,data));}
}
