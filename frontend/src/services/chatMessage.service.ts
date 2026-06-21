import { API_ENDPOINTS, buildApiUrl } from "../config/api";
import type {
  ChatMessage,
  ChatMessageListResponse,
  SendChatMessageRequest,
  SendChatMessageResponse,
} from "../types/chatMessage";

type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

type ChatMessageResponse = Omit<ChatMessage, "role" | "status"> & {
  role: string;
  status: string;
};

type ChatMessageListApiResponse = Omit<ChatMessageListResponse, "items"> & {
  items: ChatMessageResponse[];
};

type SendChatMessageApiResponse = {
  userMessage: ChatMessageResponse;
  assistantMessage: ChatMessageResponse;
};

const getAuthHeaders = (accessToken: string): HeadersInit => ({
  Authorization: `Bearer ${accessToken}`,
});

const normalizeMessage = (message: ChatMessageResponse): ChatMessage => ({
  ...message,
  role: message.role.toLowerCase() as ChatMessage["role"],
  status: message.status.toLowerCase() as ChatMessage["status"],
});

export async function getChatSessionMessages(
  accessToken: string,
  chatSessionId: string,
): Promise<ChatMessageListResponse> {
  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.chatMessages.listByChatSession(chatSessionId)),
    {
      method: "GET",
      headers: getAuthHeaders(accessToken),
      credentials: "include",
    },
  );

  if (!response.ok) {
    throw new Error("Không thể tải danh sách tin nhắn");
  }

  const responseData: ApiResponse<ChatMessageListApiResponse> =
    await response.json();

  return {
    ...responseData.data,
    items: responseData.data.items.map(normalizeMessage),
  };
}

export async function sendChatSessionMessage(
  accessToken: string,
  chatSessionId: string,
  payload: SendChatMessageRequest,
): Promise<SendChatMessageResponse> {
  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.chatMessages.sendToChatSession(chatSessionId)),
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
    throw new Error("Gửi tin nhắn thất bại");
  }

  const responseData: ApiResponse<SendChatMessageApiResponse> =
    await response.json();

  return {
    userMessage: normalizeMessage(responseData.data.userMessage),
    assistantMessage: normalizeMessage(responseData.data.assistantMessage),
  };
}

export async function getChatMessageDetail(
  accessToken: string,
  messageId: string,
): Promise<ChatMessage> {
  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.chatMessages.detail(messageId)),
    {
      method: "GET",
      headers: getAuthHeaders(accessToken),
      credentials: "include",
    },
  );

  if (!response.ok) {
    throw new Error("Không thể tải chi tiết tin nhắn");
  }

  const responseData: ApiResponse<ChatMessageResponse> = await response.json();

  return normalizeMessage(responseData.data);
}