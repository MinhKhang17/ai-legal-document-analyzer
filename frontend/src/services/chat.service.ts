import { API_ENDPOINTS, buildApiUrl } from "../config/api";
import type {
  WorkspaceChatConversation,
  WorkspaceChatMessage,
  WorkspaceChatSession,
} from "../types/chat";

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

interface ApiErrorResponse {
  message?: string;
  error?: string;
}

type SendMessageRequest = {
  message: string;
  documentId?: string;
};

const getAuthHeaders = (accessToken: string): HeadersInit => ({
  Authorization: `Bearer ${accessToken}`,
});

const readResponseBody = async <T>(
  response: Response,
): Promise<{ data: T | null; rawText: string }> => {
  const rawText = await response.text();
  const contentType = response.headers.get("content-type") ?? "";

  if (!contentType.includes("application/json") || rawText.trim().length === 0) {
    return { data: null, rawText };
  }

  try {
    return { data: JSON.parse(rawText) as T, rawText };
  } catch {
    return { data: null, rawText };
  }
};

const getApiErrorMessage = (
  errorResponse: ApiErrorResponse | null,
  rawText: string,
  fallback: string,
): string => {
  if (errorResponse?.message?.trim()) {
    return errorResponse.message.trim();
  }
  if (errorResponse?.error?.trim()) {
    return errorResponse.error.trim();
  }
  if (rawText.trim()) {
    return rawText.trim();
  }
  return fallback;
};

const requestJson = async <TResponse>(
  endpointPath: string,
  requestInit: RequestInit,
  errorMessage: string,
): Promise<TResponse> => {
  const response = await fetch(buildApiUrl(endpointPath), requestInit);
  const { data, rawText } = await readResponseBody<TResponse | ApiResponse<TResponse> | ApiErrorResponse>(response);

  if (!response.ok) {
    throw new Error(getApiErrorMessage(data as ApiErrorResponse | null, rawText, errorMessage));
  }

  if (data === null) {
    throw new Error(errorMessage);
  }

  return data as TResponse;
};

const getJson = async <TResponse>(
  endpointPath: string,
  errorMessage: string,
  accessToken: string,
): Promise<TResponse> =>
  requestJson<TResponse>(
    endpointPath,
    {
      method: "GET",
      headers: getAuthHeaders(accessToken),
      credentials: "include",
    },
    errorMessage,
  );

const postJson = async <TResponse>(
  endpointPath: string,
  payload: object,
  errorMessage: string,
  accessToken: string,
): Promise<TResponse> =>
  requestJson<TResponse>(
    endpointPath,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(accessToken),
      },
      credentials: "include",
      body: JSON.stringify(payload),
    },
    errorMessage,
  );

const mapSession = (session: WorkspaceChatSession): WorkspaceChatSession => ({
  ...session,
  status: session.status.toLowerCase(),
});

const mapMessage = (message: WorkspaceChatMessage): WorkspaceChatMessage => ({
  ...message,
  role: message.role.toLowerCase(),
  status: message.status.toLowerCase(),
});

export async function getWorkspaceChatSessions(
  accessToken: string,
  workspaceId: string,
  page = 0,
  size = 10,
  status = "ACTIVE",
): Promise<PageResponse<WorkspaceChatSession>> {
  const response = await getJson<ApiResponse<PageResponse<WorkspaceChatSession>>>(
    `${API_ENDPOINTS.chat.workspaceSessions(workspaceId)}?page=${page}&size=${size}&status=${encodeURIComponent(status)}`,
    "Không thể tải danh sách chat sessions",
    accessToken,
  );

  return {
    ...response.data,
    items: response.data.items.map(mapSession),
  };
}

export async function getChatSessionDetail(
  accessToken: string,
  chatSessionId: string,
): Promise<WorkspaceChatSession> {
  const response = await getJson<ApiResponse<WorkspaceChatSession>>(
    API_ENDPOINTS.chat.sessionDetail(chatSessionId),
    "Không thể tải chat session",
    accessToken,
  );

  return mapSession(response.data);
}

export async function getChatSessionMessages(
  accessToken: string,
  chatSessionId: string,
  page = 0,
  size = 50,
): Promise<PageResponse<WorkspaceChatMessage>> {
  const response = await getJson<ApiResponse<PageResponse<WorkspaceChatMessage>>>(
    `${API_ENDPOINTS.chat.sessionMessages(chatSessionId)}?page=${page}&size=${size}`,
    "Không thể tải lịch sử chat",
    accessToken,
  );

  return {
    ...response.data,
    items: response.data.items.map(mapMessage),
  };
}

export async function createChatSession(
  accessToken: string,
  workspaceId: string,
  title: string,
): Promise<WorkspaceChatSession> {
  const response = await postJson<ApiResponse<WorkspaceChatSession>>(
    API_ENDPOINTS.chat.workspaceSessions(workspaceId),
    { title },
    "Không thể tạo chat session",
    accessToken,
  );

  return mapSession(response.data);
}

export async function sendWorkspaceMessage(
  accessToken: string,
  workspaceId: string,
  message: string,
  documentId?: string,
): Promise<WorkspaceChatConversation> {
  const response = await postJson<ApiResponse<WorkspaceChatConversation>>(
    API_ENDPOINTS.chat.workspaceMessages(workspaceId),
    { message, ...(documentId ? { documentId } : {}) } satisfies SendMessageRequest,
    "Không thể gửi câu hỏi",
    accessToken,
  );

  return {
    chatSession: mapSession(response.data.chatSession),
    userMessage: mapMessage(response.data.userMessage),
    assistantMessage: mapMessage(response.data.assistantMessage),
  };
}

export async function sendChatSessionMessage(
  accessToken: string,
  chatSessionId: string,
  message: string,
  documentId?: string,
): Promise<WorkspaceChatConversation> {
  const response = await postJson<ApiResponse<WorkspaceChatConversation>>(
    API_ENDPOINTS.chat.sessionMessages(chatSessionId),
    { message, ...(documentId ? { documentId } : {}) } satisfies SendMessageRequest,
    "Không thể gửi câu hỏi",
    accessToken,
  );

  return {
    chatSession: mapSession(response.data.chatSession),
    userMessage: mapMessage(response.data.userMessage),
    assistantMessage: mapMessage(response.data.assistantMessage),
  };
}
