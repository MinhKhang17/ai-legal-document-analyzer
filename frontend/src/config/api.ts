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

export const fromEnvOrDefault = (key: string, fallback: string): string =>
  (() => {
    const configured = (import.meta.env as EnvMap)[key]?.trim();
    const unresolvedPlaceholders = new Set([
      key,
      `$${key}`,
      `\${${key}}`,
      `{{${key}}}`,
    ]);

    return !configured || unresolvedPlaceholders.has(configured)
      ? fallback
      : configured;
  })();

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
    verifyEmail: fromEnvOrDefault("VITE_AUTH_VERIFY_EMAIL_API", "/api/v1/auth/verify-email"),
    resendVerification: fromEnvOrDefault("VITE_AUTH_RESEND_VERIFICATION_API", "/api/v1/auth/resend-verification"),
    forgotPassword: fromEnvOrDefault("VITE_AUTH_FORGOT_PASSWORD_API", "/api/v1/auth/forgot-password"),
    resetPassword: fromEnvOrDefault("VITE_AUTH_RESET_PASSWORD_API", "/api/v1/auth/reset-password"),
  },

  workspaces: {
    list: fromEnv("VITE_WORKSPACES_API"),
    create: fromEnv("VITE_WORKSPACES_API"),
    detail: (workspaceId: string) =>
      fillPathParams(fromEnv("VITE_WORKSPACE_DETAIL_API"), { workspaceId }),
      documents: (workspaceId: string) =>
        fillPathParams(fromEnv("VITE_WORKSPACE_DOCUMENTS_API"), { workspaceId }),
      documentDownload: (workspaceId: string, documentId: string) =>
        `/api/v1/workspaces/${encodeURIComponent(workspaceId)}/documents/${encodeURIComponent(documentId)}/download`,
      documentDelete: (workspaceId: string, documentId: string) =>
        `/api/v1/workspaces/${encodeURIComponent(workspaceId)}/documents/${encodeURIComponent(documentId)}`,
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
      sessionDocuments: (chatSessionId: string) =>
        fillPathParams(fromEnvOrDefault("VITE_CHAT_SESSION_DOCUMENTS_API", "/api/v1/chat-sessions/:chatSessionId/documents"), {
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
    feedback: (messageId: string) =>
      fillPathParams(fromEnvOrDefault("VITE_CHAT_MESSAGE_FEEDBACK_API", "/api/v1/chat-messages/:messageId/feedback"), { messageId }),
    share: (chatSessionId: string) =>
      fillPathParams(fromEnvOrDefault("VITE_CHAT_SESSION_SHARE_API", "/api/v1/chat-sessions/:chatSessionId/share"), { chatSessionId }),
    shared: (shareToken: string) =>
      fillPathParams(fromEnvOrDefault("VITE_SHARED_CHAT_API", "/api/v1/shared/chat/:shareToken"), { shareToken }),
    exportMarkdown: (chatSessionId: string) =>
      fillPathParams(fromEnvOrDefault("VITE_CHAT_SESSION_MARKDOWN_EXPORT_API", "/api/v1/chat-sessions/:chatSessionId/export/markdown"), { chatSessionId }),
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
    usageSummary: fromEnvOrDefault("VITE_SUBSCRIPTION_USAGE_API", "/api/v1/subscriptions/usage"),
    refunds: fromEnv("VITE_SUBSCRIPTION_REFUNDS_API"),
    myRefunds: fromEnvOrDefault("VITE_SUBSCRIPTION_MY_REFUNDS_API", "/api/v1/subscriptions/refunds/me"),
    refundDetail: (refundId: number | string) =>
      fillPathParams(fromEnv("VITE_SUBSCRIPTION_REFUND_DETAIL_API"), {
        refundId,
      }),
    refundStatus: (refundId: number | string) =>
      fillPathParams(fromEnvOrDefault("VITE_ADMIN_REFUND_STATUS_API", "/api/v1/subscriptions/refunds/:refundId/status"), { refundId }),
  },

  paymentTransactions: {
    base: fromEnv("VITE_PAYMENT_TRANSACTIONS_API"),
    me: fromEnv("VITE_PAYMENT_TRANSACTIONS_ME_API"),
    adminList: fromEnv("VITE_PAYMENT_TRANSACTIONS_API"),
    vnPayUrl: (transactionId: number | string) =>
      fillPathParams(fromEnv("VITE_PAYMENT_TRANSACTION_VNPAY_URL_API"), {
        transactionId,
      }),
    expertTicketVnPayUrl: (ticketId: string) =>
      `/api/v1/payment-transactions/expert-ticket/${encodeURIComponent(ticketId)}/vnpay-url`,
  },

  users: {
    list: fromEnv("VITE_USERS_API"),
    detail: (userId: number | string) =>
      fillPathParams(fromEnv("VITE_USER_DETAIL_API"), { userId }),
    profile: fromEnvOrDefault("VITE_USER_PROFILE_API", "/api/v1/users/profile"),
    changePassword: fromEnvOrDefault("VITE_CHANGE_PASSWORD_API", "/api/v1/users/change-password"),
    adminExperts: fromEnvOrDefault("VITE_ADMIN_EXPERTS_API", "/api/v1/admin/users/experts"),
    resendExpertActivation: fromEnvOrDefault("VITE_ADMIN_EXPERT_RESEND_API", "/api/v1/admin/users/experts/resend-activation"),
    delete: (userId: number | string) =>
      fillPathParams(fromEnvOrDefault("VITE_ADMIN_USER_DELETE_API", "/api/v1/admin/users/:userId"), { userId }),
    restore: (userId: number | string) =>
      fillPathParams(fromEnvOrDefault("VITE_ADMIN_USER_RESTORE_API", "/api/v1/admin/users/:userId/restore"), { userId }),
  },

  adminDocuments: {
    download: (documentId: string) => fillPathParams(fromEnvOrDefault("VITE_ADMIN_DOCUMENT_DOWNLOAD_API", "/api/v1/admin/documents/:documentId/download"), { documentId }),
  },

  legalTickets: {
    base: fromEnv("VITE_LEGAL_TICKETS_API"),
    create: fromEnv("VITE_LEGAL_TICKETS_API"),
    my: fromEnv("VITE_LEGAL_TICKETS_MY_API"),
    detail: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_LEGAL_TICKET_DETAIL_API"), { ticketId }),
    messages: (ticketId: string) =>
      fillPathParams(fromEnv("VITE_LEGAL_TICKET_MESSAGES_API"), { ticketId }),
    files: (ticketId: string) =>
      fillPathParams(fromEnvOrDefault("VITE_LEGAL_TICKET_FILES_API", "/api/v1/legal-tickets/:ticketId/files"), { ticketId }),
    downloadFile: (ticketId: string, documentId: string) =>
      fillPathParams(fromEnvOrDefault("VITE_LEGAL_TICKET_FILE_DOWNLOAD_API", "/api/v1/legal-tickets/:ticketId/files/:documentId/download"), { ticketId, documentId }),
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
    adminApprove: (ticketId: string) => `/api/v1/admin/tickets/${encodeURIComponent(ticketId)}/approve`,
    adminClose: (ticketId: string) => `/api/v1/admin/tickets/${encodeURIComponent(ticketId)}/close`,
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

  expertRevenue: {
    summary: fromEnvOrDefault("VITE_EXPERT_REVENUE_API", "/api/v1/expert/revenue"),
    tickets: fromEnvOrDefault("VITE_EXPERT_REVENUE_TICKETS_API", "/api/v1/expert/revenue/tickets"),
    updatePayment: (ticketId: string) =>
      fillPathParams(fromEnvOrDefault("VITE_ADMIN_EXPERT_PAYMENT_API", "/api/v1/admin/tickets/:ticketId/expert-payment"), { ticketId }),
    resetPayment: (ticketId: string) =>
      fillPathParams(fromEnvOrDefault("VITE_ADMIN_EXPERT_PAYMENT_RESET_API", "/api/v1/admin/tickets/:ticketId/expert-payment/reset"), { ticketId }),
    periods: "/api/v1/expert/revenue/periods",
    period: (statementId: string) => `/api/v1/expert/revenue/periods/${encodeURIComponent(statementId)}`,
    periodExport: (statementId: string) => `/api/v1/expert/revenue/periods/${encodeURIComponent(statementId)}/export`,
    policies: "/api/v1/expert/revenue/commission-policies",
    notifications: "/api/v1/expert/revenue/commission-notifications",
    earlyPayouts: "/api/v1/expert/revenue/early-payouts",
  },

  adminRevenue: {
    overview: fromEnvOrDefault("VITE_ADMIN_REVENUE_OVERVIEW_API", "/api/v1/admin/revenue/overview"),
    settings: fromEnvOrDefault("VITE_ADMIN_REVENUE_SETTINGS_API", "/api/v1/admin/revenue/settings"),
    periods: "/api/v1/admin/revenue/periods",
    period: (id: string) => `/api/v1/admin/revenue/periods/${encodeURIComponent(id)}`,
    statements: (id: string) => `/api/v1/admin/revenue/periods/${encodeURIComponent(id)}/experts`,
    statement: (id: string) => `/api/v1/admin/revenue/statements/${encodeURIComponent(id)}`,
    policies: "/api/v1/admin/revenue/commission-policies",
    changeRequests: "/api/v1/admin/revenue/commission-change-requests",
    earlyPayouts: "/api/v1/admin/revenue/early-payouts",
    audit: "/api/v1/admin/revenue/audit",
  },

  aiLegal: {
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
      fillPathParams(fromEnvOrDefault("VITE_ADMIN_KNOWLEDGE_BASE_INGESTED_DOCUMENTS_API", "/api/v1/admin/knowledge-bases/:knowledgeBaseEntryId/ingested-documents"), {
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
    ingestionJob: (jobId: string) =>
      fillPathParams(fromEnvOrDefault("VITE_ADMIN_KNOWLEDGE_BASE_INGESTION_JOB_API", "/api/v1/admin/knowledge-base/ingestion-jobs/:jobId"), {
        jobId,
      }),
    failIngestionJob: (jobId: string) =>
      fillPathParams(fromEnvOrDefault("VITE_ADMIN_KNOWLEDGE_BASE_FAIL_JOB_API", "/api/v1/admin/knowledge-base/ingestion-jobs/:jobId/failed"), {
        jobId,
      }),
    unpublish: (knowledgeBaseEntryId: string) =>
      fillPathParams(fromEnvOrDefault("VITE_ADMIN_KNOWLEDGE_BASE_UNPUBLISH_API", "/api/v1/admin/knowledge-base/:knowledgeBaseEntryId/unpublish"), {
        knowledgeBaseEntryId,
      }),
    archive: (knowledgeBaseEntryId: string) =>
      fillPathParams(fromEnv("VITE_ADMIN_KNOWLEDGE_BASE_ARCHIVE_API"), {
        knowledgeBaseEntryId,
      }),
    sourceFile: (knowledgeBaseEntryId: string) =>
      `/api/v1/admin/knowledge-base/${encodeURIComponent(knowledgeBaseEntryId)}/source-file`,
  },

  contracts: {
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
    adminChatRatings: fromEnvOrDefault("VITE_ADMIN_CHAT_FEEDBACK_API", "/api/v1/admin/chat-messages/feedback"),
  },

  aiService: {
    root: fromEnv("VITE_AI_ROOT_API"),
    health: fromEnv("VITE_AI_HEALTH_API"),
    technologies: fromEnv("VITE_AI_TECHNOLOGIES_API"),
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
