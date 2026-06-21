export type ChatMessageRole = "user" | "assistant" | "system";
export type ChatMessageStatus = "sent" | "processing" | "completed" | "failed";

export interface ChatMessage {
  messageId: string;
  chatSessionId?: string;
  workspaceId?: string;
  role: ChatMessageRole;
  content: string;
  status: ChatMessageStatus;
  requestId?: string | null;
  aiModel?: string | null;
  promptTokens?: number | null;
  completionTokens?: number | null;
  totalTokens?: number | null;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt?: string;
}

export interface SendChatMessageRequest {
  message: string;
}

export interface SendChatMessageResponse {
  userMessage: ChatMessage;
  assistantMessage: ChatMessage;
}

export interface ChatMessageListResponse {
  items: ChatMessage[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}