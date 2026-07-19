import type { RefundStatus } from '../types/subscription';

export interface RefundFormValues { amount: string; reason: string }

export const REFUND_STATUSES: readonly RefundStatus[] = [
  'NEW',
  'ADMIN_REVIEWING',
  'WAITING_USER_BANK_INFO',
  'WAITING_EMAIL_CONFIRMATION',
  'EMAIL_CONFIRMED',
  'REFUND_REQUEST_CREATED',
  'REFUNDED',
  'REQUESTED',
  'APPROVED',
  'REJECTED',
  'PROCESSING',
  'COMPLETED',
  'CLOSED',
];

const REFUND_STATUS_TRANSITIONS: Readonly<Record<RefundStatus, readonly RefundStatus[]>> = {
  NEW: ['ADMIN_REVIEWING', 'REJECTED'],
  ADMIN_REVIEWING: ['WAITING_USER_BANK_INFO', 'WAITING_EMAIL_CONFIRMATION', 'REJECTED'],
  WAITING_USER_BANK_INFO: ['WAITING_EMAIL_CONFIRMATION', 'REJECTED'],
  WAITING_EMAIL_CONFIRMATION: [],
  EMAIL_CONFIRMED: ['REFUND_REQUEST_CREATED', 'REJECTED'],
  REFUND_REQUEST_CREATED: ['REFUNDED', 'REJECTED'],
  REFUNDED: ['CLOSED'],
  CLOSED: [],
  REQUESTED: ['APPROVED', 'REJECTED'],
  APPROVED: ['PROCESSING'],
  REJECTED: [],
  PROCESSING: ['COMPLETED'],
  COMPLETED: [],
};

export const getAllowedRefundStatusTransitions = (
  status: RefundStatus,
): readonly RefundStatus[] => REFUND_STATUS_TRANSITIONS[status];

export const validateRefundForm = (values: RefundFormValues, maximumAmount: number): string | null => {
  const amount = Number(values.amount);
  if (!Number.isFinite(amount) || amount <= 0) return 'validation.refund.amountPositive';
  if (amount > maximumAmount) return 'validation.refund.amountMaximum';
  const reason = values.reason.trim();
  if (!reason) return 'validation.refund.reasonRequired';
  if (reason.length > 2000) return 'validation.refund.reasonMaximum';
  return null;
};
