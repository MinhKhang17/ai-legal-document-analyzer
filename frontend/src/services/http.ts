import { buildAiServiceUrl, buildApiUrl } from "../config/api";
import { getAccessToken } from "./authSession";
import { getRuntimeLanguage, translate } from "../utils/i18n";

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

const runtimeMessage = (key: string) => translate(getRuntimeLanguage(), key);
export const getAccessDeniedMessage = () => runtimeMessage("errors.accessDenied");
export const getBackendUnavailableMessage = () => runtimeMessage("errors.backendUnavailable");
export const getAiServiceUnavailableMessage = () => runtimeMessage("errors.aiServiceUnavailable");

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

  const accessToken = getAccessToken();
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
  void errorResponse;
  void rawText;
  void fallback;
  return runtimeMessage("errors.requestFailed");
};

const getStatusFallbackMessage = (status: number, fallback: string): string => {
  if (status === 401) {
    return runtimeMessage("errors.sessionExpired");
  }

  if (status === 403) {
    return getAccessDeniedMessage();
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
    getBackendUnavailableMessage(),
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
    getAiServiceUnavailableMessage(),
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
