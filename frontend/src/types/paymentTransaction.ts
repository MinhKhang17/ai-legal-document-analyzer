import type { PaymentMethod } from "./subscription";

export type PaymentStatus =
  | "PENDING"
  | "SUCCESS"
  | "FAILED"
  | "CANCELLED"
  | "EXPIRED"
  | string;

export interface PaymentTransaction {
  id: number;
  customerId: number;
  subscriptionPlanId: number;
  planName: string;
  customerPlanId: number;
  amount: number;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  transactionCode: string | null;
  paymentUrl: string | null;
  gatewayTransactionNo: string | null;
  gatewayResponseCode: string | null;
  paidAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentUrlResponse {
  transactionId: number;
  transactionCode: string | null;
  provider: string;
  paymentUrl: string;
}
