type EnvMap = Record<string, string | undefined>;

const getRequiredEnvValue = (key: string): string => {
  const value = (import.meta.env as EnvMap)[key]?.trim();

  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`Missing required environment variable: ${key}`);
  }

  return value;
};

export const API_BASE_URL = getRequiredEnvValue("VITE_API_BASE_URL");
export const AI_SERVICE_BASE_URL = getRequiredEnvValue(
  "VITE_AI_SERVICE_BASE_URL",
);

export const API_ENDPOINTS = {
  auth: {
    login: getRequiredEnvValue("VITE_AUTH_LOGIN_API"),
    register: getRequiredEnvValue("VITE_AUTH_REGISTER_API"),
    refresh: getRequiredEnvValue("VITE_AUTH_REFRESH_API"),
    me: getRequiredEnvValue("VITE_AUTH_ME_API"),
    logout: getRequiredEnvValue("VITE_AUTH_LOGOUT_API"),
  },

  workspaces: {
    list: getRequiredEnvValue("VITE_WORKSPACE_LIST_API"),
    create: getRequiredEnvValue("VITE_WORKSPACE_CREATE_API"),

    detail: (workspaceId: string) =>
      `${getRequiredEnvValue("VITE_WORKSPACE_DETAIL_API")}/${workspaceId}`,

    documents: (workspaceId: string) =>
      `${getRequiredEnvValue("VITE_WORKSPACE_DOCUMENTS_API")}/${workspaceId}/documents`,
  },

  chat: {
    workspaceSessions: (workspaceId: string) =>
      `${getRequiredEnvValue("VITE_CHAT_WORKSPACE_SESSIONS_API")}/${workspaceId}/chat-sessions`,
    sessionDetail: (chatSessionId: string) =>
      `${getRequiredEnvValue("VITE_CHAT_SESSIONS_API")}/${chatSessionId}`,
    sessionMessages: (chatSessionId: string) =>
      `${getRequiredEnvValue("VITE_CHAT_SESSIONS_API")}/${chatSessionId}/messages`,
    messageDetail: (messageId: string) =>
      `${getRequiredEnvValue("VITE_CHAT_MESSAGES_API")}/${messageId}`,
    workspaceMessages: (workspaceId: string) =>
      `${getRequiredEnvValue("VITE_CHAT_WORKSPACE_MESSAGES_API")}/${workspaceId}/messages`,
  },

  subscription: {
    plans: getRequiredEnvValue("VITE_SUBSCRIPTION_PLANS_API"),
    planDetail: (planId: number | string) =>
      `${getRequiredEnvValue("VITE_SUBSCRIPTION_PLANS_API")}/${planId}`,
    customerPlanSubscribe: getRequiredEnvValue(
      "VITE_CUSTOMER_PLAN_SUBSCRIBE_API",
    ),
    customerPlanMe: getRequiredEnvValue("VITE_CUSTOMER_PLAN_ME_API"),
    customerPlanCancel: (customerPlanId: number | string) =>
      `${getRequiredEnvValue("VITE_CUSTOMER_PLAN_CANCEL_API")}/${customerPlanId}/cancel`,
  },

  paymentTransactions: {
    me: getRequiredEnvValue("VITE_PAYMENT_TRANSACTIONS_ME_API"),
    adminList: getRequiredEnvValue("VITE_PAYMENT_TRANSACTIONS_ADMIN_API"),
    vnPayUrl: (transactionId: number | string) =>
      `${getRequiredEnvValue("VITE_PAYMENT_TRANSACTION_VNPAY_URL_API")}/${transactionId}/vnpay-url`,
    vnPayReturn: getRequiredEnvValue(
      "VITE_PAYMENT_TRANSACTION_VNPAY_RETURN_API",
    ),
    vnPayIpn: getRequiredEnvValue("VITE_PAYMENT_TRANSACTION_VNPAY_IPN_API"),
    simulateSuccess: (transactionId: number | string) =>
      `${getRequiredEnvValue("VITE_PAYMENT_TRANSACTIONS_ADMIN_API")}/${transactionId}/success`,
    simulateFailed: (transactionId: number | string) =>
      `${getRequiredEnvValue("VITE_PAYMENT_TRANSACTIONS_ADMIN_API")}/${transactionId}/failed`,
  },

  users: {
    list: getRequiredEnvValue("VITE_USERS_API"),
    detail: (userId: number | string) =>
      `${getRequiredEnvValue("VITE_USERS_API")}/${userId}`,
  },

  legalTickets: {
    create: getRequiredEnvValue("VITE_LEGAL_TICKETS_API"),
    detail: (ticketId: string) =>
      `${getRequiredEnvValue("VITE_LEGAL_TICKETS_API")}/${ticketId}`,
    adminList: getRequiredEnvValue("VITE_ADMIN_LEGAL_TICKETS_API"),
    assignLawyer: (ticketId: string) =>
      `${getRequiredEnvValue("VITE_ADMIN_LEGAL_TICKETS_API")}/${ticketId}/assign-lawyer`,
  },

  lawyerTickets: {
    my: getRequiredEnvValue("VITE_LAWYER_TICKETS_MY_API"),

    detail: (ticketId: number | string) =>
      `${getRequiredEnvValue("VITE_LAWYER_TICKETS_API")}/${ticketId}`,

    messages: (ticketId: number | string) =>
      `${getRequiredEnvValue("VITE_LAWYER_TICKETS_API")}/${ticketId}/messages`,

    files: (ticketId: number | string) =>
      `${getRequiredEnvValue("VITE_LAWYER_TICKETS_API")}/${ticketId}/files`,

    close: (ticketId: number | string) =>
      `${getRequiredEnvValue("VITE_LAWYER_TICKETS_API")}/${ticketId}/close`,
  },
  aiLegal: {
    query: getRequiredEnvValue("VITE_AI_LEGAL_QUERY_API"),
  },

  aiService: {
    root: getRequiredEnvValue("VITE_AI_ROOT_API"),
    health: getRequiredEnvValue("VITE_AI_HEALTH_API"),
    technologies: getRequiredEnvValue("VITE_AI_TECHNOLOGIES_API"),
  },

  aiRag: {
    internalQuery: getRequiredEnvValue("VITE_AI_INTERNAL_RAG_QUERY_API"),
    testQuery: getRequiredEnvValue("VITE_AI_TEST_QUERY_API"),
  },

  aiKnowledge: {
    supportedFormats: getRequiredEnvValue(
      "VITE_AI_KNOWLEDGE_SUPPORTED_FORMATS_API",
    ),
    ingest: getRequiredEnvValue("VITE_AI_KNOWLEDGE_INGEST_API"),
    ingestV2: getRequiredEnvValue("VITE_AI_KNOWLEDGE_INGEST_V2_API"),
    search: getRequiredEnvValue("VITE_AI_KNOWLEDGE_SEARCH_API"),
    ask: getRequiredEnvValue("VITE_AI_KNOWLEDGE_ASK_API"),
    queryV2: getRequiredEnvValue("VITE_AI_KNOWLEDGE_QUERY_V2_API"),
  },

  aiInternalDocuments: {
    import: getRequiredEnvValue("VITE_AI_INTERNAL_DOCUMENTS_IMPORT_API"),
    process: getRequiredEnvValue("VITE_AI_INTERNAL_DOCUMENTS_PROCESS_API"),
  },

  aiRiskKnowledge: {
    supportedFormats: getRequiredEnvValue(
      "VITE_AI_RISK_KNOWLEDGE_SUPPORTED_FORMATS_API",
    ),
    import: getRequiredEnvValue("VITE_AI_RISK_KNOWLEDGE_IMPORT_API"),
    importV2: getRequiredEnvValue("VITE_AI_RISK_KNOWLEDGE_IMPORT_V2_API"),
    query: getRequiredEnvValue("VITE_AI_RISK_KNOWLEDGE_QUERY_API"),
    queryV2: getRequiredEnvValue("VITE_AI_RISK_KNOWLEDGE_QUERY_V2_API"),
  },

  aiContracts: {
    supportedFormats: getRequiredEnvValue(
      "VITE_AI_CONTRACT_SUPPORTED_FORMATS_API",
    ),
    upload: getRequiredEnvValue("VITE_AI_CONTRACT_UPLOAD_API"),
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
  options: { allowRootEndpoint?: boolean } = {},
): string => {
  const normalizedEndpoint = normalizeEndpoint(endpoint);

  if (!normalizedEndpoint || normalizedEndpoint === "/") {
    if (!options.allowRootEndpoint) {
      throw new Error(invalidEndpointMessage);
    }
  }

  const normalizedBaseUrl = stripTrailingSlashes(baseUrl);

  return `${normalizedBaseUrl}${normalizedEndpoint}`;
};

export const buildApiUrl = (endpoint: string): string =>
  buildServiceUrl(
    API_BASE_URL,
    endpoint,
    "Missing or invalid backend API endpoint path. Check VITE_AUTH_*, VITE_WORKSPACE_*, VITE_CHAT_*, VITE_SUBSCRIPTION_*, VITE_PAYMENT_*, VITE_USERS_*, and VITE_LEGAL_TICKETS_* env values.",
  );

export const buildAiServiceUrl = (endpoint: string): string =>
  buildServiceUrl(
    AI_SERVICE_BASE_URL,
    endpoint,
    "Missing or invalid AI-service endpoint path. Check VITE_AI_* env values.",
    { allowRootEndpoint: true },
  );
