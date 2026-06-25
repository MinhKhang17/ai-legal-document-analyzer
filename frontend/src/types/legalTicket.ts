export interface CreateLegalTicketRequest {
  request_id?: string | null;
  workspace_id: string;
  document_id?: string | null;
  question: string;
  answer: string;
  confidence_score?: number | null;
  should_suggest_ticket?: boolean | null;
  suggestion_type?: string | null;
  suggestion_reason?: string | null;
  missing_information?: string | null;
  risk_level?: string | null;
  legal_domain?: string | null;
  user_action_hint?: string | null;
}

export interface LegalTicket {
  id: string;
  request_id: string | null;
  workspace_id: string | null;
  document_id: string | null;
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
  status: string | null;
  assigned_lawyer_id: string | null;
  assigned_lawyer_name: string | null;
  created_at: string | null;
  updated_at: string | null;
}
