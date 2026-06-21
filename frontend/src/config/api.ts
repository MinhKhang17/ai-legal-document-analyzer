type EnvMap = Record<string, string | undefined>;

const getRequiredEnvValue = (key: string): string => {
  const value = (import.meta.env as EnvMap)[key]?.trim();

  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`Missing required environment variable: ${key}`);
  }

  return value;
};

export const API_BASE_URL = getRequiredEnvValue("VITE_API_BASE_URL");

export const API_ENDPOINTS = {
  auth: {
    login: getRequiredEnvValue("VITE_AUTH_LOGIN_API"),
    register: getRequiredEnvValue("VITE_AUTH_REGISTER_API"),
    refresh: getRequiredEnvValue("VITE_AUTH_REFRESH_API"),
    me: getRequiredEnvValue("VITE_AUTH_ME_API"),
  },

  workspaces: {
    list: getRequiredEnvValue("VITE_WORKSPACE_LIST_API"),
    create: getRequiredEnvValue("VITE_WORKSPACE_CREATE_API"),

    detail: (workspaceId: string) =>
      `${getRequiredEnvValue("VITE_WORKSPACE_DETAIL_API")}/${workspaceId}`,

    documents: (workspaceId: string) =>
      `${getRequiredEnvValue("VITE_WORKSPACE_DOCUMENTS_API")}/${workspaceId}/documents`,
  },
  chatSessions: {
    create: (workspaceId: string) =>
      `${getRequiredEnvValue("VITE_WORKSPACE_CHAT_SESSIONS_API")}/${workspaceId}/chat-sessions`,

    listByWorkspace: (workspaceId: string) =>
      `${getRequiredEnvValue("VITE_WORKSPACE_CHAT_SESSIONS_API")}/${workspaceId}/chat-sessions`,

    detail: (chatSessionId: string) =>
      `${getRequiredEnvValue("VITE_CHAT_SESSION_DETAIL_API")}/${chatSessionId}`,

    update: (chatSessionId: string) =>
      `${getRequiredEnvValue("VITE_CHAT_SESSION_DETAIL_API")}/${chatSessionId}`,

    delete: (chatSessionId: string) =>
      `${getRequiredEnvValue("VITE_CHAT_SESSION_DETAIL_API")}/${chatSessionId}`,
  },
  chatMessages: {
    listByChatSession: (chatSessionId: string) =>
      `${getRequiredEnvValue("VITE_CHAT_SESSION_MESSAGES_API")}/${chatSessionId}/messages`,

    sendToChatSession: (chatSessionId: string) =>
      `${getRequiredEnvValue("VITE_CHAT_SESSION_MESSAGES_API")}/${chatSessionId}/messages`,

    detail: (messageId: string) =>
      `${getRequiredEnvValue("VITE_CHAT_MESSAGE_DETAIL_API")}/${messageId}`,
  },
  subscription: {
    plans: getRequiredEnvValue("VITE_SUBSCRIPTION_PLANS_API"),
    customerPlanSubscribe: getRequiredEnvValue(
      "VITE_CUSTOMER_PLAN_SUBSCRIBE_API",
    ),
    customerPlanMe: getRequiredEnvValue("VITE_CUSTOMER_PLAN_ME_API"),
  },
} as const;

const stripTrailingSlashes = (value: string): string =>
  value.replace(/\/+$/, "");

const normalizeEndpoint = (endpoint: string): string => {
  const trimmedEndpoint = endpoint.trim();
  const withoutLeadingSlash = trimmedEndpoint.replace(/^\/+/, "");
  const singleLeadingSlash = `/${withoutLeadingSlash.replace(/\/{2,}/g, "/")}`;

  return singleLeadingSlash.replace(/^\/$/, "/");
};

const buildServiceUrl = (
  baseUrl: string,
  endpoint: string,
  invalidEndpointMessage: string,
): string => {
  const normalizedEndpoint = normalizeEndpoint(endpoint);

  if (!normalizedEndpoint || normalizedEndpoint === "/") {
    throw new Error(invalidEndpointMessage);
  }

  const normalizedBaseUrl = stripTrailingSlashes(baseUrl);

  return `${normalizedBaseUrl}${normalizedEndpoint}`;
};

export const buildApiUrl = (endpoint: string): string =>
  buildServiceUrl(
    API_BASE_URL,
    endpoint,
    "Missing or invalid backend API endpoint path. Check VITE_AUTH_*, VITE_WORKSPACE_*, VITE_CHAT_SESSION_*, VITE_SUBSCRIPTION_*, and VITE_CUSTOMER_PLAN_* env values.",
  );
