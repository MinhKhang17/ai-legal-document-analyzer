import { API_ENDPOINTS } from "../config/api";
import type { PaymentTransaction, PaymentUrlResponse } from "../types/paymentTransaction";
import { buildAuthHeaders, requestApiData } from "./http";

export const getMyPaymentTransactions = async (): Promise<PaymentTransaction[]> =>
  requestApiData<PaymentTransaction[]>(
    API_ENDPOINTS.paymentTransactions.me,
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể tải lịch sử thanh toán",
  );

export const getAllPaymentTransactions = async (): Promise<PaymentTransaction[]> =>
  requestApiData<PaymentTransaction[]>(
    API_ENDPOINTS.paymentTransactions.adminList,
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể tải toàn bộ giao dịch thanh toán",
  );

export const createVnPayPaymentUrl = async (
  transactionId: number | string,
): Promise<PaymentUrlResponse> =>
  requestApiData<PaymentUrlResponse>(
    API_ENDPOINTS.paymentTransactions.vnPayUrl(transactionId),
    {
      method: "POST",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể tạo URL thanh toán VNPAY",
  );

export const handleVnPayReturn = async (
  params: URLSearchParams,
): Promise<PaymentTransaction> => {
  const queryString = params.toString();

  return requestApiData<PaymentTransaction>(
    `${API_ENDPOINTS.paymentTransactions.vnPayReturn}${queryString ? `?${queryString}` : ""}`,
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể xử lý kết quả thanh toán VNPAY",
  );
};

export const simulatePaymentSuccess = async (
  transactionId: number | string,
): Promise<PaymentTransaction> =>
  requestApiData<PaymentTransaction>(
    API_ENDPOINTS.paymentTransactions.simulateSuccess(transactionId),
    {
      method: "PUT",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể giả lập giao dịch thành công",
  );

export const simulatePaymentFailed = async (
  transactionId: number | string,
): Promise<PaymentTransaction> =>
  requestApiData<PaymentTransaction>(
    API_ENDPOINTS.paymentTransactions.simulateFailed(transactionId),
    {
      method: "PUT",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể giả lập giao dịch thất bại",
  );
