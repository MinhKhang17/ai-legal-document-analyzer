export type RegisterRequest = {
  firstName: string;
  lastName: string;
  workspaceName: string;
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

  if (typeof data.message === 'string' && data.message.length > 0) {
    return data.message;
  }

  if ('error' in data && typeof data.error === 'string' && data.error.length > 0) {
    return data.error;
  }

  return null;
};

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.trim() ?? '';

const buildEndpoint = (path: string) => {
  if (!API_BASE_URL) {
    return path;
  }

  return `${API_BASE_URL.replace(/\/+$/, '')}${path}`;
};

export async function register(payload: RegisterRequest): Promise<RegisterResponse> {
  const response = await fetch(buildEndpoint('/api/auth/v1/register'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  const data = (await response.json().catch(() => null)) as RegisterResponse | RegisterErrorResponse | null;

  if (!response.ok) {
    throw new Error(getErrorMessage(data) ?? 'Registration failed. Please try again.');
  }

  return (data ?? {}) as RegisterResponse;
}
