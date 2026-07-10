type EnvMap = Record<string, string | undefined>;

type EndpointParams = Record<string, string | number>;

const getRequiredEnvValue = (key: string): string => {
  const value = (import.meta.env as EnvMap)[key]?.trim();

  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`Missing required environment variable: ${key}`);
  }

  return value;
};

const fromEnv = (key: string) => getRequiredEnvValue(key);

const fillPathParams = (template: string, params: EndpointParams): string =>
  Object.entries(params).reduce(
    (endpoint, [key, value]) =>
      endpoint.replace(`:${key}`, encodeURIComponent(String(value))),
    template,
  );

export const API_BASE_URL = fromEnv("VITE_API_BASE_URL");
export const AI_SERVICE_BASE_URL = fromEnv("VITE_AI_SERVICE_BASE_URL");

export const API_ENDPOINTS = {
  auth: {
    login: fromEnv("VITE_AUTH_LOGIN_API"),
    register: fromEnv("VITE_AUTH_REGISTER_API"),
    refresh: fromEnv("VITE_AUTH_REFRESH_API"),
    me: fromEnv("VITE_AUTH_ME_API"),
    logout: fromEnv("VITE_AUTH_LOGOUT_API"),
  },

  workspaces: {
    list: fromEnv("VITE_WORKSPACES_API"),
    create: fromEnv("VITE_WORKSPACES_API"),
    detail: (workspaceId: string) =>
      fillPathParams(fromEnv("VITE_WORKSPACE_DETAIL_API"), { workspaceId }),
    documents: (workspaceId: string) =>
      fillPathParams(fromEnv("VITE_WORKSPACE_DOCUMENTS_API"), { workspaceId }),
  },

  chat: {
    workspaceSessions: (workspaceId: string) =>
      fillPathParams(fromEnv("VITE_WORKSPACE_CHAT_SESSIONS_API"), {
        workspaceId,
      }),
    workspaceMessages: (workspaceId: string) =>
      fillPathParams(fromEnv("VITE_WORKSPACE_MESSAGES_API"), { workspaceId }),
    sessions: fromEnv("VITE_CHAT_SESSIONS_API"),
    sessionDetail: (chatSessionId: string) =>
      fillPathParams(fromEnv("VITE_CHAT_SESSION_DETAIL_API"), {
        chatSessionId,
      }),
    sessionMessages: (chatSessionId: string) =>
      fillPathParams(fromEnv("VITE_CHAT_SESSION_MESSAGES_API"), {
        chatSessionId,
      }),
    messages: fromEnv("VITE_CHAT_MESSAGES_API"),
    messageDetail: (messageId: string) =>
      fillPathParams(fromEnv("VITE_CHAT_MESSAGE_DETAIL_API"), { messageId }),
    summary: (chatSessionId: string) =>
      fillPathParams(fromEnv("VITE_CHAT_SESSION_SUMMARY_API"), {
        chatSessionId,
      }),
    memory: (chatSessionId: string) =>
      fillPathParams(fromEnv("VITE_CHAT_SESSION_MEMORY_API"), {
        chatSessionId,
      }),
    context: (chatSessionId: string) =>
      fillPathParams(fromEnv("VITE_CHAT_SESSION_CONTEXT_API"), {
        chatSessionId,
      }),
  },

  subscription: {
    plans: fromEnv("VITE_SUBSCRIPTION_PLANS_API"),
    planDetail: (planId: number | string) =>
      fillPathParams(fromEnv("VITE_SUBSCRIPTION_PLAN_DETAIL_API"), { planId }),
    subscribe: fromEnv("VITE_SUBSCRIPTION_SUBSCRIBE_API"),
    myPlan: fromEnv("VITE_SUBSCRIPTION_MY_PLAN_API"),
    cancelMyPlan: (customerPlanId: number | string) =>
      fillPathParams(fromEnv("VITE_SUBSCRIPTION_MY_PLAN_CANCEL_API"), {
        customerPlanId,
      }),
    myUsage: fromEnv("VITE_SUBSCRIPTION_MY_USAGE_API"),
    refunds: fromEnv("VITE_SUBSCRIPTION_REFUNDS_API"),
    refundDetail: (refundId: number | string) =>
      fillPathParams(fromEnv("VITE_SUBSCRIPTION_REFUND_DETAIL_API"), {
        refundId,
      }),
  },

  paymentTransactions: {
    base: fromEnv("VITE_PAYMENT_TRANSACTIONS_API"),
    me: fromEnv("VITE_PAYMENT_TRANSACTIONS_ME_API"),
    adminList: fromEnv("VITE_PAYMENT_TRANSACTIONS_API"),
    vnPayUrl: (transactionId: number | string) =>
      fillPathParams(fromEnv("VITE_PAYMENT_TRANSACTION_VNPAY_URL_API"), {
        transactionId,
      }),
    vnPayReturn: fromEnv("VITE_PAYMENT_VNPAY_RETURN_API"),
    vnPayIpn: fromEnv("VITE_PAYMENT_VNPAY_IPN_API"),
  },

  users: {
    list: fromEnv("VITE_USERS_API"),
    detail: (userId: number | string) =>
      fillPathParams(fromEnv("VITE_USER_DETAIL_API"), { userId }),
  },

  legalTickets: {
    base: fromEnv("VITE_LEGAL_TICKETS_API"),
    create: fromEnv("VITE_LEGAL_TICKETS_API"),
    my: fromEnv("VITE_LEGAL_TICKETS_MY_API"),
    detail: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_LEGAL_TICKET_DETAIL_API"), { ticketId }),
    messages: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_LEGAL_TICKET_MESSAGES_API"), { ticketId }),
    cancel: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_LEGAL_TICKET_CANCEL_API"), { ticketId }),
    customerReply: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_LEGAL_TICKET_CUSTOMER_REPLY_API"), {
        ticketId,
      }),
    close: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_LEGAL_TICKET_CLOSE_API"), { ticketId }),
    reopen: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_LEGAL_TICKET_REOPEN_API"), { ticketId }),
    adminList: fromEnv("VITE_ADMIN_TICKETS_API"),
    adminDetail: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_TICKET_DETAIL_API"), { ticketId }),
    adminSummary: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_TICKET_SUMMARY_API"), { ticketId }),
    adminChatHistory: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_TICKET_CHAT_HISTORY_API"), {
        ticketId,
      }),
    adminFiles: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_TICKET_FILES_API"), { ticketId }),
    assignLawyer: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_TICKET_ASSIGN_LAWYER_API"), {
        ticketId,
      }),
    reassignLawyer: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_TICKET_REASSIGN_LAWYER_API"), {
        ticketId,
      }),
    reject: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_TICKET_REJECT_API"), { ticketId }),
  },

  lawyerTickets: {
    base: fromEnv("VITE_LAWYER_TICKETS_API"),
    my: fromEnv("VITE_LAWYER_TICKETS_MY_API"),
    detail: (ticketId: number | string) =>
      fillPathParams(fromEnv("VITE_LAWYER_TICKET_DETAIL_API"), { ticketId }),
    messages: (ticketId: number | string) =>
      fillPathParams(fromEnv("VITE_LAWYER_TICKET_MESSAGES_API"), { ticketId }),
    files: (ticketId: number | string) =>
      fillPathParams(fromEnv("VITE_LAWYER_TICKET_FILES_API"), { ticketId }),
    close: (ticketId: number | string) =>
      fillPathParams(fromEnv("VITE_LAWYER_TICKET_CLOSE_API"), { ticketId }),
    download: (ticketId: number | string, documentId: number | string) =>
      fillPathParams(fromEnv("VITE_LAWYER_TICKET_DOWNLOAD_API"), {
        ticketId,
        documentId,
      }),
  },

  aiLegal: {
    query: fromEnv("VITE_AI_LEGAL_QUERY_API"),
    ticketAssessment: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_AI_TICKET_ASSESSMENT_API"), { ticketId }),
    ticketSummary: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_AI_TICKET_SUMMARY_API"), { ticketId }),
    ticketCitations: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_AI_TICKET_CITATIONS_API"), { ticketId }),
    chatCitations: (chatMessageId: string) =>
      fillPathParams(fromEnv("VITE_AI_CHAT_CITATIONS_API"), {
        chatMessageId,
      }),
  },

  knowledgeBase: {
    list: fromEnv("VITE_ADMIN_KNOWLEDGE_BASE_API"),
    upload: fromEnv("VITE_ADMIN_KNOWLEDGE_BASE_UPLOAD_API"),
    detail: (knowledgeBaseEntryId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_KNOWLEDGE_BASE_DETAIL_API"), {
        knowledgeBaseEntryId,
      }),
    ingestedDocuments: (knowledgeBaseEntryId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_KNOWLEDGE_BASE_INGESTED_DOCUMENTS_API"), {
        knowledgeBaseEntryId,
      }),
    versions: (knowledgeBaseEntryId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_KNOWLEDGE_BASE_VERSIONS_API"), {
        knowledgeBaseEntryId,
      }),
    ingest: (knowledgeBaseEntryId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_KNOWLEDGE_BASE_INGEST_API"), {
        knowledgeBaseEntryId,
      }),
    review: (knowledgeBaseEntryId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_KNOWLEDGE_BASE_REVIEW_API"), {
        knowledgeBaseEntryId,
      }),
    publish: (knowledgeBaseEntryId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_KNOWLEDGE_BASE_PUBLISH_API"), {
        knowledgeBaseEntryId,
      }),
    archive: (knowledgeBaseEntryId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_KNOWLEDGE_BASE_ARCHIVE_API"), {
        knowledgeBaseEntryId,
      }),
  },

  contracts: {
    templates: fromEnv("VITE_CONTRACT_TEMPLATES_API"),
    templateDetail: (templateId: number | string) =>
      fillPathParams(fromEnv("VITE_CONTRACT_TEMPLATE_DETAIL_API"), {
        templateId,
      }),
    generate: fromEnv("VITE_CONTRACT_GENERATE_API"),
    base: fromEnv("VITE_CONTRACTS_API"),
    my: fromEnv("VITE_MY_CONTRACTS_API"),
    detail: (contractId: string) =>
      fillPathParams(fromEnv("VITE_CONTRACT_DETAIL_API"), { contractId }),
    versions: (contractId: string) =>
      fillPathParams(fromEnv("VITE_CONTRACT_VERSIONS_API"), { contractId }),
    revertVersion: (contractId: string, versionNo: number | string) =>
      fillPathParams(fromEnv("VITE_CONTRACT_REVERT_VERSION_API"), {
        contractId,
        versionNo,
      }),
  },

  feedback: {
    adminSurveys: fromEnv("VITE_ADMIN_FEEDBACK_SURVEYS_API"),
    adminSurveyDetail: (surveyId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_FEEDBACK_SURVEY_DETAIL_API"), {
        surveyId,
      }),
    surveyResponses: (surveyId: string) =>
      fillPathParams(fromEnv("VITE_FEEDBACK_SURVEY_RESPONSES_API"), {
        surveyId,
      }),
    aiReports: fromEnv("VITE_FEEDBACK_AI_REPORTS_API"),
    adminAiReports: fromEnv("VITE_ADMIN_FEEDBACK_AI_REPORTS_API"),
    adminAiReportDetail: (reportId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_FEEDBACK_AI_REPORT_DETAIL_API"), {
        reportId,
      }),
  },

  aiService: {
    root: fromEnv("VITE_AI_ROOT_API"),
    health: fromEnv("VITE_AI_HEALTH_API"),
    technologies: fromEnv("VITE_AI_TECHNOLOGIES_API"),
  },

  aiRag: {
    internalQuery: fromEnv("VITE_AI_RAG_QUERY_API"),
    preview: fromEnv("VITE_AI_RAG_PREVIEW_API"),
  },

  aiInternalDocuments: {
    import: fromEnv("VITE_AI_DOCUMENT_IMPORT_API"),
    process: fromEnv("VITE_AI_DOCUMENT_PROCESS_API"),
  },

  aiRiskKnowledge: {
    supportedFormats: fromEnv("VITE_AI_RISK_KNOWLEDGE_SUPPORTED_FORMATS_API"),
    import: fromEnv("VITE_AI_RISK_KNOWLEDGE_IMPORT_API"),
    importV2: fromEnv("VITE_AI_RISK_KNOWLEDGE_IMPORT_V2_API"),
    query: fromEnv("VITE_AI_RISK_KNOWLEDGE_QUERY_API"),
    queryV2: fromEnv("VITE_AI_RISK_KNOWLEDGE_QUERY_V2_API"),
  },

  aiContracts: {
    supportedFormats: fromEnv("VITE_AI_CONTRACTS_SUPPORTED_FORMATS_API"),
    upload: fromEnv("VITE_AI_CONTRACTS_UPLOAD_API"),
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
    "Missing or invalid backend API endpoint path. Check frontend VITE_* API env values.",
  );

export const buildAiServiceUrl = (endpoint: string): string =>
  buildServiceUrl(
    AI_SERVICE_BASE_URL,
    endpoint,
    "Missing or invalid AI-service endpoint path. Check frontend VITE_AI_* env values.",
    { allowRootEndpoint: true },
  );
