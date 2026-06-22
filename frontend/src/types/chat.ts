export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
  status?: 'thinking' | 'streaming' | 'done' | 'error';
  errorMessage?: string;
  citations?: string[];
}

export interface ChatThread {
  id: string;
  title: string;
  documentName: string;
  updatedAt: string;
  messages: ChatMessage[];
  riskTags: string[];
}

export interface WorkspaceChatSession {
  chatSessionId: string;
  workspaceId: string;
  title: string;
  status: string;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
  lastMessageAt: string | null;
}

export interface WorkspaceChatMessage {
  messageId: string;
  chatSessionId: string;
  role: string;
  messageType: string;
  content: string;
  status: string;
  requestId: string | null;
  aiModel: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WorkspaceChatConversation {
  chatSession: WorkspaceChatSession;
  userMessage: WorkspaceChatMessage;
  assistantMessage: WorkspaceChatMessage;
}

export interface WorkspaceChatRequest {
  message: string;
  documentId?: string;
}
