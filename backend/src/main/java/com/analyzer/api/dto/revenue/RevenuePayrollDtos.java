package com.analyzer.api.dto.revenue;

import com.analyzer.api.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class RevenuePayrollDtos {
    private RevenuePayrollDtos() {}
    public record Period(String id,String periodCode,LocalDate startDate,LocalDate endDate,LocalDateTime cutoffAt,
        RevenuePeriodStatus status,LocalDateTime closedAt,BigDecimal totalGross,BigDecimal totalPlatformFee,
        BigDecimal totalExpertPayout,BigDecimal totalAdjustments,BigDecimal totalFinalPayout,long version) {}
    public record Statement(String id,Period period,Long expertId,String expertNameSnapshot,long ticketCount,
        BigDecimal grossConsultationFee,BigDecimal totalPlatformFee,BigDecimal totalExpertPayout,
        BigDecimal adjustmentAmount,BigDecimal finalPayout,BigDecimal paidAmount,BigDecimal remainingAmount,
        RevenueStatementStatus status,LocalDateTime generatedAt,LocalDateTime confirmedAt,LocalDateTime paidAt,
        String paymentReference,long version,List<Item> items,List<Adjustment> adjustments,List<Payout> payouts) {}
    public record Item(String id,String ticketId,String ticketCode,BigDecimal consultationFee,BigDecimal commissionRateSnapshot,
        BigDecimal platformFee,BigDecimal expertPayout,LocalDateTime recognizedAt,Long assignedExpertIdSnapshot,
        LegalTicketStatus ticketStatusSnapshot) {}
    public record Adjustment(String id,String originalPeriodId,String appliedPeriodId,Long expertId,String ticketId,
        RevenueAdjustmentType type,BigDecimal amount,String reason,Long createdById,LocalDateTime createdAt) {}
    public record Policy(String id,BigDecimal rate,LocalDate effectiveFrom,LocalDate effectiveTo,CommissionPolicyStatus status,
        String reason,String sourceChangeRequestId,Long createdById,LocalDateTime createdAt,LocalDateTime activatedAt,long version) {}
    public record ChangeRequest(String id,BigDecimal oldRateSnapshot,BigDecimal newRate,CommissionApplicationType applicationType,
        LocalDate effectiveFrom,String reason,CommissionChangeRequestStatus status,Long requestedById,
        LocalDateTime requestedAt,LocalDateTime tokenExpiresAt,LocalDateTime verifiedAt,long version) {}
    public record PolicyNotification(Long id,String policyId,BigDecimal rate,LocalDate effectiveFrom,
        NotificationDeliveryStatus status,int retryCount,LocalDateTime sentAt,LocalDateTime failedAt,LocalDateTime readAt) {}
    public record EarlyPayout(String id,String requestCode,Long expertId,String expertName,String periodId,String periodCode,
        String statementId,BigDecimal requestedAmount,BigDecimal eligibleAmountSnapshot,BigDecimal approvedAmount,
        BigDecimal currentAvailableAmount,String reason,String expertNote,String adminNote,EarlyPayoutStatus status,
        LocalDateTime requestedAt,LocalDateTime reviewedAt,Long reviewedById,LocalDateTime approvedAt,
        LocalDateTime rejectedAt,LocalDateTime paidAt,String paymentReference,long version) {}
    public record Payout(String id,Long expertId,String statementId,String earlyPayoutRequestId,BigDecimal amount,
        PayoutTransactionType type,PayoutTransactionStatus status,LocalDateTime paidAt,String paymentReference,Long paidById) {}
    public record Audit(String id,String action,Long actorId,String entityType,String entityId,String oldValuesJson,
        String newValuesJson,String reason,String requestId,LocalDateTime createdAt) {}

    public record CreateCommissionChange(@NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal newRate,
        @NotBlank @Size(max=2000) String reason,@NotNull CommissionApplicationType applicationType) {}
    public record VerifyCommission(@NotBlank String token) {}
    public record CreateAdjustment(@NotNull Long expertId,String ticketId,@NotNull RevenueAdjustmentType type,
        @NotNull @DecimalMin("-1000000000") @DecimalMax("1000000000") BigDecimal amount,
        @NotBlank @Size(max=2000) String reason) {}
    public record CreateEarlyPayout(@NotBlank String statementId,@NotNull @DecimalMin("0.01") @DecimalMax("1000000000") BigDecimal requestedAmount,
        @NotBlank @Size(max=2000) String reason,String expertNote,String idempotencyKey) {}
    public record EarlyPayoutNote(@NotBlank @Size(max=2000) String note) {}
    public record ApproveEarlyPayout(@NotNull @DecimalMin("0.01") @DecimalMax("1000000000") BigDecimal approvedAmount,String adminNote) {}
    public record RejectEarlyPayout(@NotBlank @Size(max=2000) String reason) {}
    public record MarkPayoutPaid(@NotBlank @Size(max=255) String paymentReference,@NotBlank @Size(max=255) String idempotencyKey) {}
    public record MarkStatementPayment(@NotBlank @Size(max=255) String paymentReference,@NotBlank @Size(max=255) String idempotencyKey) {}
}
