import { API_ENDPOINTS, buildApiUrl } from '../config/api';
import { getAccessDeniedMessage, getBackendUnavailableMessage } from './http';
import { getAccessToken } from './authSession';
import type {
  ApiResponse,
  CustomerPlan,
  PageResponse,
  RefundRequestPayload,
  RefundRequestRecord,
  RefundStatus,
  SubscribeCustomerPlanRequest,
  SubscriptionUsage,
  SubscriptionPlanRequest,
  SubscriptionPlan,
  UpdateRefundStatusPayload,
  SubscriptionUsageSummary,
} from '../types/subscription';

type ParsedResponse<T> = {
  data: T | null;
  rawText: string;
};

interface ApiErrorResponse {
  message?: string;
  error?: string;
  data?: unknown;
  errors?: unknown;
}

export class SubscriptionRequestError extends Error {
  readonly status: number;
  readonly details: ApiErrorResponse | null;
  readonly rawText: string;

  constructor(status: number, message: string, details: ApiErrorResponse | null, rawText: string) {
    super(message);
    this.name = 'SubscriptionRequestError';
    this.status = status;
    this.details = details;
    this.rawText = rawText;
  }
}

export const isSubscriptionRequestError = (error: unknown): error is SubscriptionRequestError =>
  error instanceof SubscriptionRequestError;

export const isSubscriptionUnauthorizedError = (error: unknown): error is SubscriptionRequestError =>
  isSubscriptionRequestError(error) && (error.status === 401 || error.status === 403);

export const isCustomerPlanMissingError = (error: unknown): error is SubscriptionRequestError => {
  if (!isSubscriptionRequestError(error) || (error.status !== 400 && error.status !== 404)) {
    return false;
  }

  const normalizedMessage = error.message.toLocaleLowerCase('vi-VN');

  return (
    normalizedMessage.includes('chưa đăng ký') ||
    normalizedMessage.includes('chưa sở hữu') ||
    normalizedMessage.includes('chua dang ky') ||
    normalizedMessage.includes('chua so huu') ||
    normalizedMessage.includes('không có gói') ||
    normalizedMessage.includes('khong co goi') ||
    normalizedMessage.includes('no plan') ||
    normalizedMessage.includes('not found')
  );
};

const getStoredAccessToken = (): string | undefined => getAccessToken();

const readResponseBody = async <T>(response: Response): Promise<ParsedResponse<T>> => {
  const rawText = await response.text();
  const contentType = response.headers.get('content-type') ?? '';

  if (!contentType.includes('application/json') || rawText.trim().length === 0) {
    return {
      data: null,
      rawText,
    };
  }

  try {
    return {
      data: (JSON.parse(rawText) as T) ?? null,
      rawText,
    };
  } catch {
    return {
      data: null,
      rawText,
    };
  }
};

const getApiErrorMessage = (
  errorResponse: ApiErrorResponse | null,
  rawText: string,
  fallback: string,
): string => {
  const normalizeMessage = (message: string) =>
    message.trim().toLowerCase() === 'access denied'
      ? getAccessDeniedMessage()
      : message.trim();

  if (errorResponse?.message && errorResponse.message.trim().length > 0) {
    return normalizeMessage(errorResponse.message);
  }

  if (errorResponse?.error && errorResponse.error.trim().length > 0) {
    return normalizeMessage(errorResponse.error);
  }

  const normalizedText = rawText.trim();

  if (normalizedText.length > 0) {
    return normalizeMessage(normalizedText);
  }

  return fallback;
};

const buildAuthHeaders = (headers: HeadersInit = {}): HeadersInit => {
  const accessToken = getStoredAccessToken();

  return accessToken
    ? {
        ...headers,
        Authorization: `Bearer ${accessToken}`,
      }
    : headers;
};

const requestJson = async <TResponse>(
  endpointPath: string,
  requestInit: RequestInit,
  errorMessage: string,
): Promise<TResponse> => {
  let response: Response;

  try {
    response = await fetch(buildApiUrl(endpointPath), requestInit);
  } catch (error) {
    const rawText = error instanceof Error ? error.message : '';
    throw new SubscriptionRequestError(
      0,
      getBackendUnavailableMessage(),
      { error: rawText },
      rawText,
    );
  }

  const { data, rawText } = await readResponseBody<TResponse | ApiErrorResponse>(response);

  if (!response.ok) {
    throw new SubscriptionRequestError(
      response.status,
      getApiErrorMessage(data as ApiErrorResponse | null, rawText, errorMessage),
      (data as ApiErrorResponse | null) ?? null,
      rawText,
    );
  }

  if (data === null) {
    throw new SubscriptionRequestError(response.status, errorMessage, null, rawText);
  }

  return data as TResponse;
};

const getJson = async <TResponse>(endpointPath: string, errorMessage: string): Promise<TResponse> =>
  requestJson<TResponse>(
    endpointPath,
    {
      method: 'GET',
      headers: buildAuthHeaders({ Accept: 'application/json' }),
      credentials: 'include',
    },
    errorMessage,
  );

const postJson = async <TResponse>(
  endpointPath: string,
  payload: object,
  errorMessage: string,
): Promise<TResponse> =>
  requestJson<TResponse>(
    endpointPath,
    {
      method: 'POST',
      headers: buildAuthHeaders({
        Accept: 'application/json',
        'Content-Type': 'application/json',
      }),
      credentials: 'include',
      body: JSON.stringify(payload),
    },
    errorMessage,
  );

const putJson = async <TResponse>(
  endpointPath: string,
  errorMessage: string,
  payload?: object,
): Promise<TResponse> =>
  requestJson<TResponse>(
    endpointPath,
    {
      method: 'PUT',
      headers: buildAuthHeaders({
        Accept: 'application/json',
        ...(payload ? { 'Content-Type': 'application/json' } : {}),
      }),
      credentials: 'include',
      body: payload ? JSON.stringify(payload) : undefined,
    },
    errorMessage,
  );

const deleteJson = async <TResponse>(
  endpointPath: string,
  errorMessage: string,
): Promise<TResponse> =>
  requestJson<TResponse>(
    endpointPath,
    {
      method: 'DELETE',
      headers: buildAuthHeaders({
        Accept: 'application/json',
      }),
      credentials: 'include',
    },
    errorMessage,
  );

export const getSubscriptionPlans = async (): Promise<ApiResponse<SubscriptionPlan[]>> =>
  getJson<ApiResponse<SubscriptionPlan[]>>(
    API_ENDPOINTS.subscription.plans,
    'Unable to load subscription plans.',
  );

export const getSubscriptionPlanById = async (
  planId: number | string,
): Promise<ApiResponse<SubscriptionPlan>> =>
  getJson<ApiResponse<SubscriptionPlan>>(
    API_ENDPOINTS.subscription.planDetail(planId),
    'Unable to load subscription plan detail.',
  );

export const createSubscriptionPlan = async (
  payload: SubscriptionPlanRequest,
): Promise<ApiResponse<SubscriptionPlan>> =>
  postJson<ApiResponse<SubscriptionPlan>>(
    API_ENDPOINTS.subscription.plans,
    payload,
    'Unable to create subscription plan.',
  );

export const updateSubscriptionPlan = async (
  planId: number | string,
  payload: SubscriptionPlanRequest,
): Promise<ApiResponse<SubscriptionPlan>> =>
  putJson<ApiResponse<SubscriptionPlan>>(
    API_ENDPOINTS.subscription.planDetail(planId),
    'Unable to update subscription plan.',
    payload,
  );

export const deleteSubscriptionPlan = async (
  planId: number | string,
): Promise<ApiResponse<void>> =>
  deleteJson<ApiResponse<void>>(
    API_ENDPOINTS.subscription.planDetail(planId),
    'Unable to delete subscription plan.',
  );

export const getMyCustomerPlan = async (): Promise<ApiResponse<CustomerPlan>> =>
  getJson<ApiResponse<CustomerPlan>>(
    API_ENDPOINTS.subscription.myPlan,
    'Unable to load current customer plan.',
  );

export const subscribeCustomerPlan = async (
  payload: SubscribeCustomerPlanRequest,
): Promise<ApiResponse<CustomerPlan>> =>
  postJson<ApiResponse<CustomerPlan>>(
    API_ENDPOINTS.subscription.subscribe,
    payload,
    'Unable to subscribe to the selected plan.',
  );

export const cancelCustomerPlan = async (
  customerPlanId: number | string,
): Promise<ApiResponse<CustomerPlan>> =>
  putJson<ApiResponse<CustomerPlan>>(
    API_ENDPOINTS.subscription.cancelMyPlan(customerPlanId),
    'Unable to cancel the current customer plan.',
  );

export const getMySubscriptionUsage = async (
  page = 0,
  size = 10,
): Promise<ApiResponse<PageResponse<SubscriptionUsage>>> => {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  return getJson<ApiResponse<PageResponse<SubscriptionUsage>>>(
    `${API_ENDPOINTS.subscription.myUsage}?${query.toString()}`,
    'Unable to load subscription usage history.',
  );
};

export const getSubscriptionUsageSummary = async (): Promise<ApiResponse<SubscriptionUsageSummary>> =>
  getJson<ApiResponse<SubscriptionUsageSummary>>(
    API_ENDPOINTS.subscription.usageSummary,
    'Unable to load subscription usage summary.',
  );

export const requestSubscriptionRefund = async (
  payload: RefundRequestPayload,
): Promise<ApiResponse<RefundRequestRecord>> =>
  postJson<ApiResponse<RefundRequestRecord>>(
    API_ENDPOINTS.subscription.refunds,
    payload,
    'Unable to submit refund request.',
  );

export const confirmSubscriptionRefundEmail = async (token: string): Promise<ApiResponse<RefundRequestRecord>> =>
  getJson<ApiResponse<RefundRequestRecord>>(
    `${API_ENDPOINTS.subscription.refunds}/confirm?${new URLSearchParams({ token }).toString()}`,
    'Unable to confirm refund email.',
  );

export const getSubscriptionRefund = async (
  refundId: number | string,
): Promise<ApiResponse<RefundRequestRecord>> =>
  getJson<ApiResponse<RefundRequestRecord>>(
    API_ENDPOINTS.subscription.refundDetail(refundId),
    'Unable to load refund request detail.',
  );

export const getMySubscriptionRefunds = async (): Promise<ApiResponse<RefundRequestRecord[]>> =>
  getJson<ApiResponse<RefundRequestRecord[]>>(API_ENDPOINTS.subscription.myRefunds, 'Unable to load refund history.');

export const getAdminSubscriptionRefunds = async (status?: RefundStatus): Promise<ApiResponse<RefundRequestRecord[]>> => {
  const query = status ? `?${new URLSearchParams({ status }).toString()}` : '';
  return getJson<ApiResponse<RefundRequestRecord[]>>(`${API_ENDPOINTS.subscription.refunds}${query}`, 'Unable to load refund requests.');
};

export const updateSubscriptionRefundStatus = async (
  refundId: number | string,
  payload: UpdateRefundStatusPayload,
): Promise<ApiResponse<RefundRequestRecord>> =>
  requestJson<ApiResponse<RefundRequestRecord>>(
    API_ENDPOINTS.subscription.refundStatus(refundId),
    { method: 'PATCH', headers: buildAuthHeaders({ Accept: 'application/json', 'Content-Type': 'application/json' }), credentials: 'include', body: JSON.stringify(payload) },
    'Unable to update refund status.',
  );
