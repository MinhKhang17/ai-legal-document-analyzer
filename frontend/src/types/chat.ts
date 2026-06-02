export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
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
