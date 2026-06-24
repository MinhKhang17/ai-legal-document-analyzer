import { buildAiServiceUrl, buildApiUrl } from "../config/api";

export interface ApiResponse<T> {
  code: number;
  message: string;
  data?: T;
}

interface ApiErrorResponse {
  message?: string;
  error?: string;
  data?: unknown;
  errors?: unknown;
}

export const ACCESS_DENIED_MESSAGE = "Tài khoản hiện tại không có quyền truy cập chức năng này.";
export const BACKEND_API_UNAVAILABLE_MESSAGE =
  "Không thể kết nối backend API. Vui lòng kiểm tra VITE_API_BASE_URL hoặc trạng thái backend.";
export const AI_SERVICE_UNAVAILABLE_MESSAGE =
  "Không thể kết nối AI-service. Vui lòng kiểm tra VITE_AI_SERVICE_BASE_URL hoặc trạng thái ai-service.";

type ParsedResponse<T> = {
  data: T | null;
  rawText: string;
};

export class ApiRequestError extends Error {
  readonly status: number;
  readonly details: ApiErrorResponse | null;
  readonly rawText: string;

  constructor(status: number, message: string, details: ApiErrorResponse | null, rawText: string) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.details = details;
    this.rawText = rawText;
  }
}

export const getReadableErrorMessage = (error: unknown, fallback: string): string => {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message.trim();
  }

  return fallback;
};

export const getUniqueErrorMessages = (errors: unknown[], fallback: string): string => {
  const messages = errors
    .map((error) => getReadableErrorMessage(error, fallback))
    .map((message) => message.trim())
    .filter(Boolean);

  return [...new Set(messages)].join(" ") || fallback;
};

export const getStoredAccessToken = (): string | undefined => {
  if (typeof window === "undefined") {
    return undefined;
  }

  const accessToken = window.localStorage.getItem("accessToken")?.trim();
  return accessToken && accessToken.length > 0 ? accessToken : undefined;
};

export const buildAuthHeaders = (headers: HeadersInit = {}): HeadersInit => {
  const accessToken = getStoredAccessToken();

  return accessToken
    ? {
        ...headers,
        Authorization: `Bearer ${accessToken}`,
      }
    : headers;
};

export const buildBearerHeaders = (
  accessToken: string,
  headers: HeadersInit = {},
): HeadersInit => ({
  ...headers,
  Authorization: `Bearer ${accessToken}`,
});

const readResponseBody = async <T>(response: Response): Promise<ParsedResponse<T>> => {
  const rawText = await response.text();
  const contentType = response.headers.get("content-type") ?? "";

  if (!contentType.includes("application/json") || rawText.trim().length === 0) {
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
  const normalizeErrorMessage = (message: string): string => {
    const normalizedMessage = message.trim();
    return normalizedMessage.toLowerCase() === "access denied"
      ? ACCESS_DENIED_MESSAGE
      : normalizedMessage;
  };

  if (errorResponse?.message && errorResponse.message.trim().length > 0) {
    return normalizeErrorMessage(errorResponse.message);
  }

  if (errorResponse?.error && errorResponse.error.trim().length > 0) {
    return normalizeErrorMessage(errorResponse.error);
  }

  const normalizedText = rawText.trim();

  if (normalizedText.length > 0) {
    return normalizeErrorMessage(normalizedText);
  }

  return fallback;
};

const getStatusFallbackMessage = (status: number, fallback: string): string => {
  if (status === 401) {
    return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  }

  if (status === 403) {
    return ACCESS_DENIED_MESSAGE;
  }

  return fallback;
};

const fetchOrThrow = async (
  url: string,
  requestInit: RequestInit,
  networkErrorMessage: string,
): Promise<Response> => {
  try {
    return await fetch(url, requestInit);
  } catch (error) {
    const rawText = error instanceof Error ? error.message : "";
    throw new ApiRequestError(0, networkErrorMessage, { error: rawText }, rawText);
  }
};

export const requestJson = async <TResponse>(
  endpointPath: string,
  requestInit: RequestInit,
  errorMessage: string,
): Promise<TResponse> => {
  const response = await fetchOrThrow(
    buildApiUrl(endpointPath),
    requestInit,
    BACKEND_API_UNAVAILABLE_MESSAGE,
  );
  const { data, rawText } = await readResponseBody<TResponse | ApiErrorResponse>(response);

  if (!response.ok) {
    const fallbackMessage = getStatusFallbackMessage(response.status, errorMessage);
    throw new ApiRequestError(
      response.status,
      getApiErrorMessage(data as ApiErrorResponse | null, rawText, fallbackMessage),
      (data as ApiErrorResponse | null) ?? null,
      rawText,
    );
  }

  if (data === null) {
    throw new ApiRequestError(response.status, errorMessage, null, rawText);
  }

  return data as TResponse;
};

export const requestAiJson = async <TResponse>(
  endpointPath: string,
  requestInit: RequestInit,
  errorMessage: string,
): Promise<TResponse> => {
  const response = await fetchOrThrow(
    buildAiServiceUrl(endpointPath),
    requestInit,
    AI_SERVICE_UNAVAILABLE_MESSAGE,
  );
  const { data, rawText } = await readResponseBody<TResponse | ApiErrorResponse>(response);

  if (!response.ok) {
    const fallbackMessage = getStatusFallbackMessage(response.status, errorMessage);
    throw new ApiRequestError(
      response.status,
      getApiErrorMessage(data as ApiErrorResponse | null, rawText, fallbackMessage),
      (data as ApiErrorResponse | null) ?? null,
      rawText,
    );
  }

  if (data === null) {
    throw new ApiRequestError(response.status, errorMessage, null, rawText);
  }

  return data as TResponse;
};

export const requestApiData = async <TResponse>(
  endpointPath: string,
  requestInit: RequestInit,
  errorMessage: string,
): Promise<TResponse> => {
  const response = await requestJson<ApiResponse<TResponse>>(
    endpointPath,
    requestInit,
    errorMessage,
  );

  if (typeof response.data === "undefined") {
    throw new ApiRequestError(200, errorMessage, null, "");
  }

  return response.data;
};

export const requestApiResponse = async <TResponse>(
  endpointPath: string,
  requestInit: RequestInit,
  errorMessage: string,
): Promise<ApiResponse<TResponse>> =>
  requestJson<ApiResponse<TResponse>>(endpointPath, requestInit, errorMessage);
