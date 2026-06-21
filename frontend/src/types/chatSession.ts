import type { Status } from './status';

export interface ChatSession {
  chatSessionId: string;
  workspaceId: string;
  title: string;
  status: Status;
  createdAt: string;
  updatedAt: string;
  lastMessageAt?: string | null;
}

export interface CreateChatSessionRequest {
  title: string;
}

export interface UpdateChatSessionRequest {
  title: string;
}

export interface ChatSessionListResponse {
  items: ChatSession[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}