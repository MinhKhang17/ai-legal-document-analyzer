type EnvMap = Record<string, string | undefined>;

const getRequiredEnvValue = (key: string): string => {
  const value = (import.meta.env as EnvMap)[key]?.trim();

  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Missing required environment variable: ${key}`);
  }

  return value;
};

export const API_BASE_URL = getRequiredEnvValue('VITE_API_BASE_URL');

export const API_ENDPOINTS = {
  auth: {
    login: getRequiredEnvValue('VITE_AUTH_LOGIN_API'),
    register: getRequiredEnvValue('VITE_AUTH_REGISTER_API'),
    refresh: getRequiredEnvValue('VITE_AUTH_REFRESH_API'),
    me: getRequiredEnvValue('VITE_AUTH_ME_API'),
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
      'Missing or invalid auth API endpoint path. Check VITE_AUTH_REGISTER_API, VITE_AUTH_LOGIN_API, VITE_AUTH_ME_API, and VITE_AUTH_REFRESH_API.'
    );
  }

  const normalizedBaseUrl = stripTrailingSlashes(API_BASE_URL);

  return `${normalizedBaseUrl}${normalizedEndpoint}`;
};
