import { API_ENDPOINTS, buildApiUrl } from '../config/api';
import type {
  ApiResponse,
  CustomerPlan,
  SubscribeCustomerPlanRequest,
  SubscriptionPlan,
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
    normalizedMessage.includes('chua dang ky') ||
    normalizedMessage.includes('no plan') ||
    normalizedMessage.includes('not found')
  );
};

const getStoredAccessToken = (): string | undefined => {
  if (typeof window === 'undefined') {
    return undefined;
  }

  const accessToken = window.localStorage.getItem('accessToken')?.trim();

  return accessToken && accessToken.length > 0 ? accessToken : undefined;
};

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
  if (errorResponse?.message && errorResponse.message.trim().length > 0) {
    return errorResponse.message.trim();
  }

  if (errorResponse?.error && errorResponse.error.trim().length > 0) {
    return errorResponse.error.trim();
  }

  const normalizedText = rawText.trim();

  if (normalizedText.length > 0) {
    return normalizedText;
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
  const response = await fetch(buildApiUrl(endpointPath), requestInit);
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

export const getSubscriptionPlans = async (): Promise<ApiResponse<SubscriptionPlan[]>> =>
  getJson<ApiResponse<SubscriptionPlan[]>>(
    API_ENDPOINTS.subscription.plans,
    'Unable to load subscription plans.',
  );

export const getMyCustomerPlan = async (): Promise<ApiResponse<CustomerPlan>> =>
  getJson<ApiResponse<CustomerPlan>>(
    API_ENDPOINTS.subscription.customerPlanMe,
    'Unable to load current customer plan.',
  );

export const subscribeCustomerPlan = async (
  payload: SubscribeCustomerPlanRequest,
): Promise<ApiResponse<CustomerPlan>> =>
  postJson<ApiResponse<CustomerPlan>>(
    API_ENDPOINTS.subscription.customerPlanSubscribe,
    payload,
    'Unable to subscribe to the selected plan.',
  );
