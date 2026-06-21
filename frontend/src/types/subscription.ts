export interface ApiResponse<T> {
  code: number;
  message: string;
  data?: T;
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
