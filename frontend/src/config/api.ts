type EnvMap = Record<string, string | undefined>;

const getEnvValue = (key: string, fallback = ''): string => {
  const value = (import.meta.env as EnvMap)[key]?.trim();

  if (typeof value !== 'string' || value.length === 0) {
    return fallback;
  }

  return value;
};

const getRequiredEnvValue = (key: string, fallback = ''): string => {
  const value = getEnvValue(key, fallback);

  if (!value) {
    throw new Error(
      `Missing required environment variable "${key}". Add "${key}" in frontend/.env or frontend/.env.local.`
    );
  }

  return value;
};

export const API_BASE_URL = getEnvValue('VITE_API_BASE_URL');

export const API_ENDPOINTS = {
  auth: {
    register: getEnvValue('VITE_AUTH_REGISTER_API', '/api/auth/v1/register'),
    login: getEnvValue('VITE_AUTH_LOGIN_API', '/api/auth/v1/login'),
  },
} as const;

const stripTrailingSlashes = (value: string): string => value.replace(/\/+$/, '');

const normalizeEndpoint = (endpoint: string): string => {
  const trimmedEndpoint = endpoint.trim();
  const withoutLeadingSlash = trimmedEndpoint.replace(/^\/+/, '');
  const singleLeadingSlash = `/${withoutLeadingSlash.replace(/\/{2,}/g, '/')}`;

  return singleLeadingSlash.replace(/^\/$/, '/');
};

export const buildApiUrl = (endpoint: string): string => {
  const normalizedEndpoint = normalizeEndpoint(endpoint);

  if (!normalizedEndpoint || normalizedEndpoint === '/') {
    throw new Error(
      'Missing or invalid auth API endpoint path. Check VITE_AUTH_REGISTER_API and VITE_AUTH_LOGIN_API.'
    );
  }

  if (/^https?:\/\//i.test(normalizedEndpoint)) {
    return normalizedEndpoint;
  }

  const baseUrl = getRequiredEnvValue('VITE_API_BASE_URL');
  const normalizedBaseUrl = stripTrailingSlashes(baseUrl);

  return `${normalizedBaseUrl}${normalizedEndpoint}`;
};
