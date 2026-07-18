import type { LegalTicketStatus } from "./legalTicketStatus";

export type LegalTicketType = "SYSTEM_ERROR" | "QUERY_ERROR" | "CONTACT_EXPERT";

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface CreateLegalTicketRequest {
  ticket_type?: LegalTicketType;
  chat_session_id?: string | null;
  chat_message_id?: string | null;
  request_id?: string | null;
  workspace_id: string;
  document_id?: string | null;
  question: string;
  issue_fingerprint?: string | null;
  customer_note?: string | null;
}

export interface AssignLawyerRequest {
  lawyer_id: number;
  admin_note?: string | null;
  force_reassign?: boolean;
}

export interface RejectLegalTicketRequest {
  reason: string;
}

export interface CustomerTicketReplyRequest {
  message: string;
}

export interface CancelLegalTicketRequest {
  reason?: string | null;
}

export interface CloseLegalTicketRequest {
  feedback?: string | null;
}

export interface ReopenLegalTicketRequest {
  reason: string;
}

export interface LegalTicket {
  id: string;
  ticket_type?: LegalTicketType | null;
  chat_session_id?: string | null;
  chat_message_id?: string | null;

  request_id: string | null;

  workspace_id: string | null;
  workspace_name?: string | null;

  document_id: string | null;
  document_name?: string | null;

  created_by_id?: number | null;
  created_by_name?: string | null;

  question: string | null;
  answer: string | null;

  confidence_score: number | null;
  should_suggest_ticket: boolean | null;

  suggestion_type: string | null;
  suggestion_reason: string | null;
  missing_information: string | null;

  risk_level: string | null;
  legal_domain: string | null;
  user_action_hint: string | null;

  status: LegalTicketStatus | null;

  assigned_lawyer_id?: number | null;
  assigned_lawyer_name?: string | null;

  issue_fingerprint?: string | null;
  customer_note?: string | null;

  issue_title?: string | null;
  issue_summary?: string | null;

  problematic_clause?: string | null;
  clause_reference?: string | null;
  page_number?: number | null;

  ai_evidence?: string | null;
  recommended_action?: string | null;

  expert_answer?: string | null;
  expert_internal_note?: string | null;

  admin_note?: string | null;
  rejection_reason?: string | null;

  assigned_at?: string | null;
  resolved_at?: string | null;
  closed_at?: string | null;
  cancelled_at?: string | null;
  reopened_at?: string | null;

  created_at: string | null;
  updated_at: string | null;
}

export interface LegalTicketMessage {
  id: string;
  ticket_id: string;

  sender_id: number;
  sender_name: string;
  sender_role: string;

  content: string;
  message_type: string;

  created_at: string;

  internal_only: boolean;
}

export interface TicketSummary {
  ticketId: string;
  confidenceScore: number | null;
  riskLevel: string | null;
  suggestionType: string | null;
  suggestionReason: string | null;
  summary: string | null;
}

export interface AdminChatHistory {
  ticketId: string;
  messages: LegalTicketMessage[];
}

export interface AdminTicketFile {
  documentId: string;
  originalFileName: string;
  fileType: string;
  fileSize: number;
  visibilityScope: string;
  uploadedAt: string;
}
