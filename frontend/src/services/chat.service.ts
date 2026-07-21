import { API_ENDPOINTS, buildApiUrl } from "../config/api";
import {
  ACCESS_DENIED_MESSAGE,
  BACKEND_API_UNAVAILABLE_MESSAGE,
} from "./http";
import type {
  DeleteChatSessionResponse,
  ChatSessionMemory,
  ChatSessionSummary,
  WorkspaceChatConversation,
  WorkspaceChatMessage,
  WorkspaceChatSession,
  ChatFeedbackRating,
  ChatFeedbackReason,
  ChatMessageFeedback,
  ShareChatSessionResponse,
  SharedChatSession,
  ChatSessionDocument,
  AiFeedbackType,
  AiFeedbackSummary,
  ChatMode,
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
  documentIds?: string[];
  mode?: ChatMode;
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
  const normalizeMessage = (message: string) =>
    message.trim().toLowerCase() === "access denied"
      ? ACCESS_DENIED_MESSAGE
      : message.trim();

  if (errorResponse?.message?.trim()) {
    return normalizeMessage(errorResponse.message);
  }
  if (errorResponse?.error?.trim()) {
    return normalizeMessage(errorResponse.error);
  }
  if (rawText.trim()) {
    return normalizeMessage(rawText);
  }
  return fallback;
};

const requestJson = async <TResponse>(
  endpointPath: string,
  requestInit: RequestInit,
  errorMessage: string,
): Promise<TResponse> => {
  let response: Response;

  try {
    response = await fetch(buildApiUrl(endpointPath), requestInit);
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    throw new Error(BACKEND_API_UNAVAILABLE_MESSAGE);
  }

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
  signal?: AbortSignal,
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
      signal,
    },
    errorMessage,
  );

const putJson = async <TResponse>(
  endpointPath: string,
  payload: object,
  errorMessage: string,
  accessToken: string,
): Promise<TResponse> =>
  requestJson<TResponse>(
    endpointPath,
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(accessToken),
      },
      credentials: "include",
      body: JSON.stringify(payload),
    },
    errorMessage,
  );

const deleteJson = async <TResponse>(
  endpointPath: string,
  errorMessage: string,
  accessToken: string,
): Promise<TResponse> =>
  requestJson<TResponse>(
    endpointPath,
    {
      method: "DELETE",
      headers: getAuthHeaders(accessToken),
      credentials: "include",
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

export async function getChatMessageDetail(
  accessToken: string,
  messageId: string,
): Promise<WorkspaceChatMessage> {
  const response = await getJson<ApiResponse<WorkspaceChatMessage>>(
    API_ENDPOINTS.chat.messageDetail(messageId),
    "Không thể tải chi tiết tin nhắn",
    accessToken,
  );

  return mapMessage(response.data);
}

export async function getChatSessionSummary(
  accessToken: string,
  chatSessionId: string,
): Promise<ChatSessionSummary> {
  const response = await getJson<ApiResponse<ChatSessionSummary>>(
    API_ENDPOINTS.chat.summary(chatSessionId),
    "Không thể tải tóm tắt chat session",
    accessToken,
  );

  return response.data;
}

export async function getChatSessionMemory(
  accessToken: string,
  chatSessionId: string,
): Promise<ChatSessionMemory> {
  const response = await getJson<ApiResponse<ChatSessionMemory>>(
    API_ENDPOINTS.chat.memory(chatSessionId),
    "Không thể tải memory chat session",
    accessToken,
  );

  return response.data;
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

export async function updateChatSession(
  accessToken: string,
  chatSessionId: string,
  title: string,
): Promise<WorkspaceChatSession> {
  const response = await putJson<ApiResponse<WorkspaceChatSession>>(
    API_ENDPOINTS.chat.sessionDetail(chatSessionId),
    { title },
    "Không thể đổi tên chat session",
    accessToken,
  );

  return mapSession(response.data);
}

export async function deleteChatSession(
  accessToken: string,
  chatSessionId: string,
): Promise<DeleteChatSessionResponse> {
  const response = await deleteJson<ApiResponse<DeleteChatSessionResponse>>(
    API_ENDPOINTS.chat.sessionDetail(chatSessionId),
    "Không thể xóa chat session",
    accessToken,
  );

  return {
    ...response.data,
    status: response.data.status.toLowerCase(),
  };
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
  signal?: AbortSignal,
): Promise<WorkspaceChatConversation> {
  const response = await postJson<ApiResponse<WorkspaceChatConversation>>(
    API_ENDPOINTS.chat.sessionMessages(chatSessionId),
    { message, ...(documentId ? { documentId } : {}) } satisfies SendMessageRequest,
    "Không thể gửi câu hỏi",
    accessToken,
    signal,
  );

  return {
    chatSession: mapSession(response.data.chatSession),
    userMessage: mapMessage(response.data.userMessage),
    assistantMessage: mapMessage(response.data.assistantMessage),
  };
}

export async function getChatSessionDocuments(accessToken: string, chatSessionId: string): Promise<ChatSessionDocument[]> {
  const response = await getJson<ApiResponse<ChatSessionDocument[]>>(
    API_ENDPOINTS.chat.sessionDocuments(chatSessionId),
    "Khong the tai danh sach tai lieu cua phien chat",
    accessToken,
  );
  return response.data;
}

export async function attachChatSessionDocument(accessToken: string, chatSessionId: string, documentId: string): Promise<ChatSessionDocument> {
  const response = await postJson<ApiResponse<ChatSessionDocument>>(
    `${API_ENDPOINTS.chat.sessionDocuments(chatSessionId)}/${documentId}`,
    {},
    "Khong the them tai lieu vao phien chat",
    accessToken,
  );
  return response.data;
}

export async function detachChatSessionDocument(accessToken: string, chatSessionId: string, documentId: string): Promise<void> {
  await deleteJson<ApiResponse<unknown>>(
    `${API_ENDPOINTS.chat.sessionDocuments(chatSessionId)}/${documentId}`,
    "Khong the go tai lieu khoi phien chat",
    accessToken,
  );
}

export async function submitChatMessageFeedback(accessToken: string, messageId: string, payload: { feedbackType?: AiFeedbackType; reason?: string; rating?: ChatFeedbackRating; reasons?: ChatFeedbackReason[]; comment?: string }): Promise<ChatMessageFeedback> {
  const response = await postJson<ApiResponse<ChatMessageFeedback>>(API_ENDPOINTS.chat.feedback(messageId), payload, "Không thể gửi đánh giá", accessToken);
  return response.data;
}

export async function shareChatSession(accessToken: string, chatSessionId: string): Promise<ShareChatSessionResponse> {
  const response = await postJson<ApiResponse<ShareChatSessionResponse>>(API_ENDPOINTS.chat.share(chatSessionId), {}, "Không thể chia sẻ phiên chat", accessToken);
  return response.data;
}

export async function deleteChatMessageFeedback(accessToken: string, messageId: string): Promise<void> {
  await deleteJson<ApiResponse<unknown>>(API_ENDPOINTS.chat.feedback(messageId), "KhÃ´ng thá»ƒ xÃ³a Ä‘Ã¡nh giÃ¡", accessToken);
}

export async function exportChatSessionMarkdown(accessToken: string, chatSessionId: string): Promise<void> {
  const response = await fetch(buildApiUrl(API_ENDPOINTS.chat.exportMarkdown(chatSessionId)), {
    method: "GET",
    headers: getAuthHeaders(accessToken),
    credentials: "include",
  });
  if (!response.ok) {
    const { data, rawText } = await readResponseBody<ApiErrorResponse>(response);
    throw new Error(getApiErrorMessage(data, rawText, "Không thể xuất Markdown"));
  }
  const disposition = response.headers.get("content-disposition") ?? "";
  const utf8Name = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const fallbackName = disposition.match(/filename="?([^";]+)"?/i)?.[1];
  const fileName = utf8Name ? decodeURIComponent(utf8Name) : fallbackName || `phan-tich-${chatSessionId}.md`;
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export async function getSharedChatSession(accessToken: string, shareToken: string): Promise<SharedChatSession> {
  const response = await getJson<ApiResponse<SharedChatSession>>(API_ENDPOINTS.chat.shared(shareToken), "Không thể tải phiên chat được chia sẻ", accessToken);
  return { ...response.data, messages: response.data.messages.map(mapMessage) };
}

export async function getAdminChatFeedback(accessToken: string, page = 0, size = 50, rating?: ChatFeedbackRating): Promise<PageResponse<ChatMessageFeedback>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (rating) query.set('rating', rating);
  const response = await getJson<ApiResponse<PageResponse<ChatMessageFeedback>>>(`${API_ENDPOINTS.feedback.adminChatRatings}?${query}`, "Không thể tải đánh giá câu trả lời AI", accessToken);
  return response.data;
}

export type AdminAiFeedbackFilters = {
  feedbackType?: AiFeedbackType;
  resolvedMode?: Exclude<ChatMode, 'AUTO'>;
  riskLevel?: string;
  fromDate?: string;
  toDate?: string;
  keyword?: string;
  page?: number;
  size?: number;
};

export async function getAdminAiFeedback(accessToken: string, filters: AdminAiFeedbackFilters = {}): Promise<PageResponse<ChatMessageFeedback>> {
  const query = new URLSearchParams({ page: String(filters.page ?? 0), size: String(filters.size ?? 20) });
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && key !== 'page' && key !== 'size' && String(value).trim()) query.set(key, String(value));
  });
  const response = await getJson<ApiResponse<PageResponse<ChatMessageFeedback>>>(`/api/v1/admin/ai-feedbacks?${query}`, "KhÃ´ng thá»ƒ táº£i pháº£n há»“i AI", accessToken);
  return response.data;
}

export async function getAdminAiFeedbackSummary(accessToken: string): Promise<AiFeedbackSummary> {
  const response = await getJson<ApiResponse<AiFeedbackSummary>>("/api/v1/admin/ai-feedbacks/summary", "KhÃ´ng thá»ƒ táº£i thá»‘ng kÃª pháº£n há»“i AI", accessToken);
  return response.data;
}
