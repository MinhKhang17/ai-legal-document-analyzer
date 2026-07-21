import type { PageResponse } from "./legalTicket";
import type { LegalTicketStatus } from "./legalTicketStatus";

export type ExpertPaymentStatus = "UNPAID" | "PENDING" | "PAID";

export interface ExpertRevenueSummary {
  assignedTicketCount: number;
  resolvedTicketCount: number;
  paidTicketCount: number;
  pendingPaymentTicketCount: number;
  totalRevenue: number;
  paidRevenue: number;
  pendingRevenue: number;
  totalPlatformFee: number;
  totalExpertPayout: number;
}

export interface ExpertRevenueTicket {
  ticketId: string;
  ticketCode?: string | null;
  ticketStatus: LegalTicketStatus;
  consultationFee: number;
  commissionRate?: number | null;
  platformFee?: number | null;
  expertPayout?: number | null;
  paymentStatus: ExpertPaymentStatus;
  resolvedAt?: string | null;
  paidAt?: string | null;
}

export type ExpertRevenueTicketPage = PageResponse<ExpertRevenueTicket>;

export interface UpdateExpertPaymentRequest {
  consultationFee: number;
  paymentStatus: ExpertPaymentStatus;
}

export interface RevenueSetting {
  commissionRate: number;
  updatedAt?: string | null;
  updatedByName?: string | null;
}

export interface ExpertRevenueBreakdown {
  expertId: number;
  expertName: string;
  ticketCount: number;
  totalConsultationFee: number;
  totalExpertPayout: number;
}

export interface AdminRevenueOverview {
  totalTicketCount: number;
  paidTicketCount: number;
  pendingPaymentTicketCount: number;
  totalConsultationFee: number;
  totalPlatformFee: number;
  totalExpertPayout: number;
  byExpert: ExpertRevenueBreakdown[];
}

export type RevenuePeriodStatus = "OPEN" | "CALCULATING" | "CLOSED" | "PAID";
export type RevenueStatementStatus = "DRAFT" | "CONFIRMED" | "PAYMENT_PENDING" | "PARTIALLY_PAID" | "PAID";
export type CommissionPolicyStatus = "SCHEDULED" | "ACTIVE" | "EXPIRED" | "CANCELLED";
export type CommissionApplicationType = "NEXT_MONTH" | "NEXT_QUARTER";
export type EarlyPayoutStatus = "PENDING_ADMIN_REVIEW" | "NEED_MORE_INFO" | "EXPERT_RESPONDED" | "APPROVED" | "REJECTED" | "CANCELLED" | "PAYMENT_PENDING" | "PAID" | "EXPIRED";
export interface RevenuePeriod { id:string; periodCode:string; startDate:string; endDate:string; cutoffAt:string; status:RevenuePeriodStatus; closedAt?:string|null; totalGross:number; totalPlatformFee:number; totalExpertPayout:number; totalAdjustments:number; totalFinalPayout:number; version:number; }
export interface RevenueStatementItem { id:string; ticketId:string; ticketCode:string; consultationFee:number; commissionRateSnapshot:number; platformFee:number; expertPayout:number; recognizedAt:string; assignedExpertIdSnapshot:number; ticketStatusSnapshot:LegalTicketStatus; }
export interface RevenueAdjustment { id:string; originalPeriodId?:string|null; appliedPeriodId:string; expertId:number; ticketId?:string|null; type:string; amount:number; reason:string; createdById:number; createdAt:string; }
export interface PayoutTransaction { id:string; expertId:number; statementId:string; earlyPayoutRequestId?:string|null; amount:number; type:"REGULAR"|"EARLY"; status:string; paidAt?:string|null; paymentReference?:string|null; paidById?:number|null; }
export interface RevenueStatement { id:string; period:RevenuePeriod; expertId:number; expertNameSnapshot:string; ticketCount:number; grossConsultationFee:number; totalPlatformFee:number; totalExpertPayout:number; adjustmentAmount:number; finalPayout:number; paidAmount:number; remainingAmount:number; status:RevenueStatementStatus; generatedAt:string; confirmedAt?:string|null; paidAt?:string|null; paymentReference?:string|null; version:number; items:RevenueStatementItem[]; adjustments:RevenueAdjustment[]; payouts:PayoutTransaction[]; }
export type RevenuePeriodPage = PageResponse<RevenuePeriod>;
export type RevenueStatementPage = PageResponse<RevenueStatement>;
export interface CommissionPolicy { id:string; rate:number; effectiveFrom:string; effectiveTo?:string|null; status:CommissionPolicyStatus; reason?:string|null; sourceChangeRequestId?:string|null; createdById?:number|null; createdAt:string; activatedAt?:string|null; version:number; }
export interface CommissionChangeRequest { id:string; oldRateSnapshot:number; newRate:number; applicationType:CommissionApplicationType; effectiveFrom:string; reason:string; status:string; requestedById:number; requestedAt:string; tokenExpiresAt?:string|null; verifiedAt?:string|null; version:number; }
export interface PolicyNotification { id:number; policyId:string; rate:number; effectiveFrom:string; status:string; retryCount:number; sentAt?:string|null; failedAt?:string|null; readAt?:string|null; }
export interface EarlyPayout { id:string; requestCode:string; expertId:number; expertName:string; periodId:string; periodCode:string; statementId:string; requestedAmount:number; eligibleAmountSnapshot:number; approvedAmount?:number|null; currentAvailableAmount:number; reason:string; expertNote?:string|null; adminNote?:string|null; status:EarlyPayoutStatus; requestedAt:string; reviewedAt?:string|null; reviewedById?:number|null; approvedAt?:string|null; rejectedAt?:string|null; paidAt?:string|null; paymentReference?:string|null; version:number; }
export type EarlyPayoutPage = PageResponse<EarlyPayout>;
