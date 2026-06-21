import { API_ENDPOINTS, buildApiUrl } from "../config/api";
import type {
  ChatSession,
  ChatSessionListResponse,
  CreateChatSessionRequest,
  UpdateChatSessionRequest,
} from "../types/chatSession";

type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

type ChatSessionResponse = Omit<ChatSession, "status"> & {
  status: string;
};

type ChatSessionListApiResponse = Omit<ChatSessionListResponse, "items"> & {
  items: ChatSessionResponse[];
};

const getAuthHeaders = (accessToken: string): HeadersInit => ({
  Authorization: `Bearer ${accessToken}`,
});

const normalizeStatus = <T extends { status: string }>(item: T) => ({
  ...item,
  status: item.status.toLowerCase(),
});

export async function createChatSession(
  accessToken: string,
  workspaceId: string,
  payload: CreateChatSessionRequest,
): Promise<ChatSession> {
  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.chatSessions.create(workspaceId)),
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(accessToken),
      },
      credentials: "include",
      body: JSON.stringify(payload),
    },
  );

  if (!response.ok) {
    throw new Error("Tạo chat session thất bại");
  }

  const responseData: ApiResponse<ChatSessionResponse> = await response.json();

  return normalizeStatus(responseData.data) as ChatSession;
}

export async function getWorkspaceChatSessions(
  accessToken: string,
  workspaceId: string,
): Promise<ChatSessionListResponse> {
  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.chatSessions.listByWorkspace(workspaceId)),
    {
      method: "GET",
      headers: getAuthHeaders(accessToken),
      credentials: "include",
    },
  );

  if (!response.ok) {
    throw new Error("Không thể tải danh sách chat session");
  }

  const responseData: ApiResponse<ChatSessionListApiResponse> =
    await response.json();

  return {
    ...responseData.data,
    items: responseData.data.items.map(
      (item) => normalizeStatus(item) as ChatSession,
    ),
  };
}

export async function getChatSessionDetail(
  accessToken: string,
  chatSessionId: string,
): Promise<ChatSession> {
  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.chatSessions.detail(chatSessionId)),
    {
      method: "GET",
      headers: getAuthHeaders(accessToken),
      credentials: "include",
    },
  );

  if (!response.ok) {
    throw new Error("Không thể tải chat session");
  }

  const responseData: ApiResponse<ChatSessionResponse> = await response.json();

  return normalizeStatus(responseData.data) as ChatSession;
}

export async function updateChatSession(
  accessToken: string,
  chatSessionId: string,
  payload: UpdateChatSessionRequest,
): Promise<ChatSession> {
  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.chatSessions.update(chatSessionId)),
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(accessToken),
      },
      credentials: "include",
      body: JSON.stringify(payload),
    },
  );

  if (!response.ok) {
    throw new Error("Cập nhật chat session thất bại");
  }

  const responseData: ApiResponse<ChatSessionResponse> = await response.json();

  return normalizeStatus(responseData.data) as ChatSession;
}

export async function deleteChatSession(
  accessToken: string,
  chatSessionId: string,
): Promise<ChatSession> {
  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.chatSessions.delete(chatSessionId)),
    {
      method: "DELETE",
      headers: getAuthHeaders(accessToken),
      credentials: "include",
    },
  );

  if (!response.ok) {
    throw new Error("Xóa chat session thất bại");
  }

  const responseData: ApiResponse<ChatSessionResponse> = await response.json();

  return normalizeStatus(responseData.data) as ChatSession;
}