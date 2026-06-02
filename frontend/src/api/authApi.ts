import { API_ENDPOINTS, buildApiUrl } from '../config/api';

export type RegisterRequest = {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
  acceptedTerms: boolean;
};

export type RegisterResponse = {
  accessToken?: string;
  refreshToken?: string;
  user?: {
    id: string;
    firstName: string;
    lastName: string;
    email: string;
    workspaceId?: string;
    workspaceName?: string;
  };
  message?: string;
};

type RegisterErrorResponse = {
  message?: string;
  error?: string;
};

const getErrorMessage = (data: RegisterResponse | RegisterErrorResponse | null): string | null => {
  if (!data) {
    return null;
  }

  if (typeof data.message === 'string' && data.message.trim().length > 0) {
    return data.message;
  }

  if ('error' in data && typeof data.error === 'string' && data.error.trim().length > 0) {
    return data.error;
  }

  return null;
};

const safeParseJson = async <T>(response: Response): Promise<T | null> => {
  try {
    return (await response.json()) as T;
  } catch {
    return null;
  }
};

export async function register(payload: RegisterRequest): Promise<RegisterResponse> {
  const response = await fetch(buildApiUrl(API_ENDPOINTS.auth.register), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  const data = await safeParseJson<RegisterResponse | RegisterErrorResponse>(response);

  if (!response.ok) {
    throw new Error(getErrorMessage(data) ?? 'Registration failed. Please try again.');
  }

  return (data ?? {}) as RegisterResponse;
}
