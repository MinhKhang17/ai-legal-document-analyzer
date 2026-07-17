import type { PaymentTransaction } from '../types/paymentTransaction';

export interface VnPayResultReference { transactionCode: string | null; responseCode: string | null }
export type PaymentResultState = 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'INVALID' | 'NOT_FOUND';

export const parseVnPayResultReference = (params: URLSearchParams): VnPayResultReference => ({
  transactionCode: params.get('vnp_TxnRef')?.trim() || null,
  responseCode: params.get('vnp_ResponseCode')?.trim() || null,
});

export const findVnPayTransaction = (transactions: PaymentTransaction[], reference: VnPayResultReference): PaymentTransaction | null => {
  if (!reference.transactionCode) return null;
  return transactions.find((transaction) => transaction.transactionCode === reference.transactionCode) ?? null;
};

export const getPaymentResultState = (transaction: PaymentTransaction | null, hasReference: boolean, notFound = false): PaymentResultState => {
  if (!hasReference) return 'INVALID';
  if (!transaction) return notFound ? 'NOT_FOUND' : 'PENDING';
  const status = transaction.paymentStatus.toUpperCase();
  if (status === 'SUCCESS') return 'SUCCESS';
  if (status === 'FAILED' || status === 'EXPIRED') return 'FAILED';
  if (status === 'CANCELLED') return 'CANCELLED';
  return 'PENDING';
};
