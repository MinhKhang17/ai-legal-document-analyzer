export interface ApiResponse<T> {
  code: number;
  message: string;
  data?: T;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface SubscriptionPlan {
  id: number;
  planName: string;
  planType: string;
  description?: string | null;
  price: number;
  durationDays: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  name?: string;
  displayName?: string;
  priceVnd?: number;
  billingCycleDays?: number;
  aiTokenLimit?: number;
  storageLimitMb?: number;
  expertTicketLimit?: number;
  allowSystemErrorTicket?: boolean;
  allowQueryErrorTicket?: boolean;
  allowContactExpertTicket?: boolean;
  features?: string[];
}

export type CustomerPlanStatus =
  | 'PENDING'
  | 'ACTIVE'
  | 'EXPIRED'
  | 'CANCELLED'
  | string;

export type PaymentMethod =
  | 'CASH'
  | 'BANK_TRANSFER'
  | 'MOMO'
  | 'VNPAY'
  | 'CREDIT_CARD';

export interface SubscribeCustomerPlanRequest {
  subscriptionPlanId: number;
  paymentMethod: PaymentMethod;
}

export interface SubscriptionPlanRequest {
  planName: string;
  planType: string;
  description?: string | null;
  price: number;
  durationDays: number;
  active?: boolean;
  name?: string;
  displayName?: string;
  priceVnd?: number;
  billingCycleDays?: number;
  aiTokenLimit: number;
  storageLimitMb: number;
  expertTicketLimit: number;
  allowSystemErrorTicket: boolean;
  allowQueryErrorTicket: boolean;
  allowContactExpertTicket: boolean;
  features: string[];
}

export interface CustomerPlan {
  id: number;
  customerId: number;
  status: CustomerPlanStatus;
  autoRenew: boolean;
  startDate: string | null;
  endDate: string | null;
  latestTransactionId: number | null;
  latestTransactionCode: string | null;
  createdAt: string;
  updatedAt: string;
  subscriptionPlan: SubscriptionPlan;
  scheduledSubscriptionPlan?: SubscriptionPlan | null;
  planChangeEffectiveAt?: string | null;
}

export interface SubscriptionUsageSummary {
  periodStart: string;
  periodEnd: string;
  aiTokensUsed: number;
  aiTokensLimit: number;
  expertTicketsUsed: number;
  expertTicketsLimit: number;
  storageUsedBytes: number;
  storageLimitBytes: number;
}

export interface SubscriptionUsage {
  id: number;
  customerPlanId: number;
  usageType: string;
  referenceId: string | null;
  consumedUnits: number;
  metadataJson: string | null;
  createdAt: string;
}

export interface RefundRequestPayload {
  paymentTransactionId: number;
  customerPlanId?: number | null;
  reason: string;
  amount: number;
  bankName: string;
  accountNumber: string;
  accountHolderName: string;
  transactionId?: string;
  invoiceId: string;
  refundReason?: string;
}

export interface RefundRequestRecord {
  id: number;
  paymentTransactionId: number;
  customerPlanId: number | null;
  requestedById: number;
  reason: string;
  status: RefundStatus;
  amount: number;
  adminNote: string | null;
  createdAt: string;
  updatedAt: string;
  legalTicketId?: string | null;
  bankName?: string | null;
  accountNumber?: string | null;
  accountHolderName?: string | null;
  invoiceId?: string | null;
  confirmationExpiresAt?: string | null;
  emailConfirmedAt?: string | null;
  emailConfirmed?: boolean;
}

export type RefundStatus = 'NEW' | 'ADMIN_REVIEWING' | 'WAITING_USER_BANK_INFO' | 'WAITING_EMAIL_CONFIRMATION' | 'EMAIL_CONFIRMED' | 'REFUND_REQUEST_CREATED' | 'REFUNDED' | 'REJECTED' | 'CLOSED' | 'REQUESTED' | 'APPROVED' | 'PROCESSING' | 'COMPLETED';

export interface UpdateRefundStatusPayload {
  status: RefundStatus;
  adminNote?: string | null;
}
