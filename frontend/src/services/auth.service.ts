import {
  API_ENDPOINTS,
  buildApiUrl,
} from '../config/api';
import type {
  ApiErrorResponse,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
} from '../types/auth';

type ParsedResponse<T> = {
  data: T | null;
  rawText: string;
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

const postJson = async <TResponse, TError>(
  endpointPath: string,
  payload: Record<string, unknown>,
  errorMessage: string,
): Promise<TResponse> => {
  const response = await fetch(buildApiUrl(endpointPath), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  const { data, rawText } = await readResponseBody<TResponse | TError>(response);

  if (!response.ok) {
    throw new Error(getApiErrorMessage(data as TError | null, rawText, errorMessage));
  }

  return (data ?? {}) as TResponse;
};

export async function register(payload: RegisterRequest): Promise<RegisterResponse> {
  return postJson<RegisterResponse, ApiErrorResponse>(
    API_ENDPOINTS.auth.register,
    payload,
    'Registration failed. Please try again.',
  );
}

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  return postJson<LoginResponse, ApiErrorResponse>(
    API_ENDPOINTS.auth.login,
    payload,
    'Login failed. Please check your email and password.',
  );
}

