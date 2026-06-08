import { API_ENDPOINTS, buildApiUrl } from "../config/api";
import type {
  ApiErrorResponse,
  AuthMeResponse,
  CurrentUser,
  LoginRequest,
  LoginResponse,
  RefreshResponse,
  RegisterRequest,
  RegisterResponse,
} from "../types/auth";

type ParsedResponse<T> = {
  data: T | null;
  rawText: string;
};

export class AuthRequestError extends Error {
  readonly status: number;
  readonly details: ApiErrorResponse | null;
  readonly rawText: string;

  constructor(status: number, message: string, details: ApiErrorResponse | null, rawText: string) {
    super(message);
    this.name = "AuthRequestError";
    this.status = status;
    this.details = details;
    this.rawText = rawText;
  }
}

export const isAuthRequestError = (error: unknown): error is AuthRequestError =>
  error instanceof AuthRequestError;

export const isAuthUnauthorizedError = (error: unknown): error is AuthRequestError =>
  isAuthRequestError(error) && (error.status === 401 || error.status === 403);

const readResponseBody = async <T>(
  response: Response,
): Promise<ParsedResponse<T>> => {
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

const requestJson = async <TResponse>(
  endpointPath: string,
  requestInit: RequestInit,
  errorMessage: string,
): Promise<TResponse> => {
  const response = await fetch(buildApiUrl(endpointPath), requestInit);
  const { data, rawText } = await readResponseBody<TResponse | ApiErrorResponse>(
    response,
  );

  if (!response.ok) {
    throw new AuthRequestError(
      response.status,
      getApiErrorMessage(data as ApiErrorResponse | null, rawText, errorMessage),
      (data as ApiErrorResponse | null) ?? null,
      rawText,
    );
  }

  if (data === null) {
    throw new AuthRequestError(response.status, errorMessage, null, rawText);
  }

  return data as TResponse;
};

const postJson = async <TResponse>(
  endpointPath: string,
  payload: object | null,
  errorMessage: string,
  headers: HeadersInit = {},
): Promise<TResponse> => {
  const defaultHeaders =
    payload === null
      ? headers
      : {
          "Content-Type": "application/json",
          ...headers,
        };

  return requestJson<TResponse>(
    endpointPath,
    {
      method: "POST",
      headers: defaultHeaders,
      credentials: "include",
      body: payload === null ? undefined : JSON.stringify(payload),
    },
    errorMessage,
  );
};

const getJson = async <TResponse>(
  endpointPath: string,
  errorMessage: string,
  authToken?: string,
): Promise<TResponse> => {
  const headers = authToken
    ? ({
        Authorization: `Bearer ${authToken}`,
      } satisfies HeadersInit)
    : undefined;

  return requestJson<TResponse>(
    endpointPath,
    {
      method: "GET",
      headers,
      credentials: "include",
    },
    errorMessage,
  );
};

export const getCurrentUser = async (accessToken: string): Promise<AuthMeResponse> => {
  return getJson<AuthMeResponse>(
    API_ENDPOINTS.auth.me,
    "Unable to load current user session.",
    accessToken,
  );
};

export const getCurrentUserData = async (
  accessToken: string,
): Promise<CurrentUser> => {
  const response = await getCurrentUser(accessToken);
  return response.data;
};

export async function register(
  payload: RegisterRequest,
): Promise<RegisterResponse> {
  return postJson<RegisterResponse>(
    API_ENDPOINTS.auth.register,
    payload,
    "Registration failed. Please try again.",
  );
}

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  return postJson<LoginResponse>(
    API_ENDPOINTS.auth.login,
    payload,
    "Login failed. Please check your email and password.",
  );
}

export async function refreshAccessToken(): Promise<RefreshResponse> {
  return postJson<RefreshResponse>(
    API_ENDPOINTS.auth.refresh,
    null,
    "Session refresh failed. Please sign in again.",
  );
}
