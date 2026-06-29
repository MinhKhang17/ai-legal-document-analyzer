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
  maxQuota: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
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
  maxQuota: number;
  active?: boolean;
}

export interface CustomerPlan {
  id: number;
  customerId: number;
  status: CustomerPlanStatus;
  autoRenew: boolean;
  startDate: string | null;
  endDate: string | null;
  usedQuota: number;
  remainingQuota: number;
  latestTransactionId: number | null;
  latestTransactionCode: string | null;
  createdAt: string;
  updatedAt: string;
  subscriptionPlan: SubscriptionPlan;
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
}

export interface RefundRequestRecord {
  id: number;
  paymentTransactionId: number;
  customerPlanId: number | null;
  requestedById: number;
  reason: string;
  status: string;
  amount: number;
  adminNote: string | null;
  createdAt: string;
  updatedAt: string;
}
