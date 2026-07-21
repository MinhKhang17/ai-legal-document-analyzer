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
