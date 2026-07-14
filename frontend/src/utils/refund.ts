import type { RefundStatus } from '../types/subscription';

export interface RefundFormValues { amount: string; reason: string }

export const REFUND_STATUSES: readonly RefundStatus[] = [
  'REQUESTED',
  'APPROVED',
  'REJECTED',
  'PROCESSING',
  'COMPLETED',
];

const REFUND_STATUS_TRANSITIONS: Readonly<Record<RefundStatus, readonly RefundStatus[]>> = {
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
