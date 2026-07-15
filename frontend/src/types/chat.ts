export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
  status?: 'thinking' | 'streaming' | 'done' | 'error';
  errorMessage?: string;
  citations?: string[];
  requestId?: string | null;
  confidenceScore?: number | null;
  shouldSuggestTicket?: boolean | null;
  suggestionType?: string | null;
  suggestionReason?: string | null;
  missingInformation?: string | null;
  riskLevel?: string | null;
  legalDomain?: string | null;
  userActionHint?: string | null;
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
  confidenceScore: number | null;
  shouldSuggestTicket: boolean | null;
  suggestionType: string | null;
  suggestionReason: string | null;
  missingInformation: string | null;
  riskLevel: string | null;
  legalDomain: string | null;
  userActionHint: string | null;
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

export interface DeleteChatSessionResponse {
  chatSessionId: string;
  status: string;
  deletedAt: string;
}

export interface ChatSessionSummary {
  chatSessionId: string;
  title: string | null;
  summary: string | null;
  generatedAt: string | null;
  messageCount: number | null;
}

export interface ChatSessionMemory {
  chatSessionId: string;
  summary: string | null;
  memoryJson: string | null;
  contextJson: string | null;
  updatedAt: string | null;
}
