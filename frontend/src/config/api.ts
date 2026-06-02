const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.trim() ?? '';

export const API_ENDPOINTS = {
  auth: {
    register: '/api/auth/v1/register',
    login: '/api/auth/v1/login',
  },
} as const;

export const buildApiUrl = (endpoint: string): string => {
  const normalizedBaseUrl = API_BASE_URL.replace(/\/+$/, '');
  const normalizedEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;

  return normalizedBaseUrl
    ? `${normalizedBaseUrl}${normalizedEndpoint}`
    : normalizedEndpoint;
};
