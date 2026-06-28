export interface LawyerTicket {
  id: string;
  request_id: string;
  workspace_id: string;
  workspace_name: string;
  document_id: string;
  document_name: string;
  created_by_id: number;
  created_by_name: string;
  question: string;
  answer: string;
  confidence_score: number;
  should_suggest_ticket: boolean;
  suggestion_type: string;
  suggestion_reason: string;
  missing_information: string;
  risk_level: string;
  legal_domain: string;
  user_action_hint: string;
  status: string;
  assigned_lawyer_id: number | null;
  assigned_lawyer_name: string | null;
  issue_fingerprint: string;
  customer_note: string;
  issue_title: string;
  issue_summary: string;
  problematic_clause: string;
  clause_reference: string;
  page_number: number | null;
  ai_evidence: string;
  recommended_action: string;
  expert_answer: string;
  expert_internal_note: string;
  admin_note: string;
  rejection_reason: string;
  assigned_at: string | null;
  resolved_at: string | null;
  closed_at: string | null;
  cancelled_at: string | null;
  reopened_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface LawyerTicketPageResponse {
  items: LawyerTicket[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type LawyerTicketDetail = LawyerTicket;

export interface LawyerTicketMessage {
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

export interface CreateLawyerTicketMessageRequest {
  message: string;
}

export interface CreateLawyerTicketMessageResponse {
  ticketId: string;
  messageId: string;
  message: string;
}

export interface LawyerTicketFile {
  documentId: string;
  originalFileName: string;
  storedFileName: string;
  filePath: string;
  fileType: string;
  fileSize: number;
  documentPurpose: string;
  visibilityScope: string;
  uploadedAt: string;
}