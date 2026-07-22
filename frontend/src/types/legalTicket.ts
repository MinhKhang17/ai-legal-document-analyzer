import type { LegalTicketStatus } from "./legalTicketStatus";

export const LEGAL_TICKET_TYPES = [
  "SYSTEM_ERROR",
  "QUERY_ERROR",
  "CONTACT_EXPERT",
  "REFUND_REQUEST",
] as const;

export type LegalTicketType = (typeof LEGAL_TICKET_TYPES)[number];

export const isLegalTicketType = (value: string): value is LegalTicketType =>
  LEGAL_TICKET_TYPES.some((ticketType) => ticketType === value);

export const LEGAL_TICKET_RISK_LEVELS = [
  "NONE",
  "LOW",
  "MEDIUM",
  "HIGH",
  "CRITICAL",
  "UNKNOWN",
] as const;

export type LegalTicketRiskLevel = (typeof LEGAL_TICKET_RISK_LEVELS)[number];

export const isLegalTicketRiskLevel = (value: string): value is LegalTicketRiskLevel =>
  LEGAL_TICKET_RISK_LEVELS.some((riskLevel) => riskLevel === value);

export type TicketRecipientType = "EXPERT" | "ADMIN";
export type TicketPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";
export type ConversationScope = "SELECTED_RESPONSE" | "RELATED_MESSAGES" | "FULL_CONVERSATION" | "TICKET_CONTEXT_ONLY";
export type SuggestionType =
  | "DIRECT_ANSWER"
  | "ASK_UPLOAD_CONTRACT"
  | "ASK_CONTRACT_TYPE"
  | "ASK_USER_ROLE"
  | "ASK_TARGET_CLAUSE"
  | "ASK_MORE_FACTS"
  | "SUGGEST_REVISE_CLAUSE"
  | "SUGGEST_NEGOTIATION"
  | "REDIRECT_TO_SUPPORTED_SCOPE"
  | "REFUSE_AND_REDIRECT"
  | "NONE"
  | "ASK_MORE_INFO"
  | "SUGGEST_LAWYER"
  | "REQUIRE_LAWYER";
export type UserActionHint =
  | "CONTINUE_CHAT"
  | "PROVIDE_MORE_INFO"
  | "CREATE_TICKET"
  | "UPLOAD_CONTRACT"
  | "CONTACT_LAWYER";

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface CreateLegalTicketRequest {
  creationSource?: "AI_CHAT" | "MANUAL_FORM";
  title?: string;
  description?: string;
  recipientType?: TicketRecipientType;
  priority?: TicketPriority;
  conversationScope?: ConversationScope;
  userMessageId?: string | null;
  assistantMessageId?: string | null;
  focusedDocumentId?: string | null;
  documentIds?: string[];
  citationIds?: string[];
  attachmentIds?: string[];
  sharedProfileFields?: Array<"DISPLAY_NAME" | "EMAIL" | "PHONE">;
  legalIssueCategory?: string;
  contractType?: string | null;
  urgency?: string;
  userExpectedOutcome?: string;
  aiAnswerSummary?: string | null;
  aiIntent?: string | null;
  consentGranted?: boolean;
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
  creationSource?: "AI_CHAT" | "MANUAL_FORM";
  legalIssueCategory?: string | null;
  contractType?: string | null;
  userExpectedOutcome?: string | null;
  userId?: number;
  userDisplayName?: string | null;
  userEmail?: string | null;
  userPhone?: string | null;
  selectedDocumentIds?: string[];
  sharedProfileFields?: string[];
  consentGrantedAt?: string | null;
  consentRevokedAt?: string | null;
  aiQuestion?: string | null;
  aiAnswerSummary?: string | null;
  aiIntent?: string | null;
  aiRiskLevel?: LegalTicketRiskLevel | null;
  aiConfidence?: number | null;
  ticketComplexity?: "BASIC" | "STANDARD" | "COMPLEX" | "OUT_OF_SCOPE" | null;
  classificationReason?: string | null;
  classifiedAt?: string | null;
  pricingType?: "PLAN_INCLUDED" | "PAID" | null;
  userPrice?: number | null;
  internalTicketValue?: number | null;
  quoteStatus?: "DRAFT" | "SENT" | "ACCEPTED" | "REJECTED" | "EXPIRED" | null;
  paymentStatus?: "NOT_REQUIRED" | "UNPAID" | "PENDING" | "PAID" | "FAILED" | "REFUNDED" | "PARTIALLY_REFUNDED" | null;
  quotaCycle?: string | null;
  quotaReservationStatus?: "NOT_APPLICABLE" | "PENDING" | "RESERVED" | "CONSUMED" | "RELEASED" | "RESTORED" | null;
  proposedExpertId?: number | null;
  proposedExpertName?: string | null;
  assignmentOfferedAt?: string | null;
  acceptanceDueAt?: string | null;
  acceptedAt?: string | null;
  startedAt?: string | null;
  firstResponseDueAt?: string | null;
  firstRespondedAt?: string | null;
  resolutionDueAt?: string | null;
  lastExpertActivityAt?: string | null;
  slaStatus?: "ON_TRACK" | "WAITING_FOR_USER" | "WARNING" | "OVERDUE" | "BREACHED" | "EXTENDED" | null;
  ticketCode?: string;
  title?: string;
  description?: string;
  recipientType?: TicketRecipientType;
  priority?: TicketPriority;
  conversationScope?: ConversationScope;
  sourceUserMessageId?: string;
  sourceAssistantMessageId?: string;
  focusedDocumentId?: string;
  sharedDocumentIds?: string[];
  contextSnapshot?: TicketContextSnapshot;
  id: string;
  ticket_type?: LegalTicketType;
  chat_session_id?: string;
  chat_message_id?: string;

  request_id: string;

  workspace_id?: string;
  workspace_name?: string;

  document_id?: string;
  document_name?: string;

  created_by_id?: number;
  created_by_name?: string;

  question: string;
  answer?: string;

  confidence_score?: number;
  should_suggest_ticket?: boolean;

  suggestion_type?: SuggestionType;
  suggestion_reason?: string;
  missing_information?: string;

  risk_level?: LegalTicketRiskLevel;
  legal_domain?: string;
  user_action_hint?: UserActionHint;

  status: LegalTicketStatus;

  assigned_lawyer_id?: number;
  assigned_lawyer_name?: string;

  issue_fingerprint?: string;
  customer_note?: string;

  issue_title?: string;
  issue_summary?: string;

  problematic_clause?: string;
  clause_reference?: string;
  page_number?: number;

  ai_evidence?: string;
  recommended_action?: string;

  expert_answer?: string;
  expert_internal_note?: string;
  consultation_fee?: number;
  commission_rate?: number;
  platform_fee?: number;
  expert_payout?: number;
  expert_payment_status?: "UNPAID" | "PENDING" | "PAID";
  expert_paid_at?: string;

  admin_note?: string;
  rejection_reason?: string;

  assigned_at?: string;
  resolved_at?: string;
  closed_at?: string;
  cancelled_at?: string;
  reopened_at?: string;

  created_at: string;
  updated_at: string;
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
  replyToMessageId?: string | null;
  editedAt?: string | null;
  attachments?: TicketAttachment[];
}

export interface TicketContextSnapshot {
  id: string;
  userQuestion: string;
  assistantAnswer?: string | null;
  conversationTitle?: string | null;
  citationSnapshotJson?: string | null;
  documentSnapshotJson?: string | null;
  selectedMessageSnapshotJson?: string | null;
  contentHash: string;
  createdAt: string;
}

export interface TicketAttachment {
  id: string;
  originalFileName: string;
  mimeType: string;
  sizeBytes: number;
  scanStatus: string;
  uploadStatus: string;
  createdAt: string;
  downloadUrl?: string;
}

export interface AttachmentPolicy {
  maxAttachmentSizeKb: number;
  maxAttachmentsPerMessage: number;
  maxAttachmentsPerTicket: number;
  allowedMimeTypes: string[];
}

export interface ConversationShare {
  id: string;
  ticketId: string;
  shareUrl: string;
  scope: ConversationScope;
  accessMode: string;
  expiresAt: string;
  revokedAt?: string | null;
  createdAt: string;
}

export interface TicketSummary {
  ticketId: string;
  confidenceScore: number | null;
  riskLevel: LegalTicketRiskLevel | null;
  suggestionType: SuggestionType | null;
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
