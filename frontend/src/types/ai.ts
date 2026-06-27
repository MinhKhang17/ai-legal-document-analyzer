export interface AiRootStatus {
  service: string;
  version: string;
  status: string;
}

export interface AiHealthStatus {
  status: string;
  service: string;
}

export interface AiQueryChunk {
  chunk_id: string;
  title: string;
  text: string;
  score: number;
  context: Array<Record<string, unknown>>;
}

export interface AiRetrievedChunk {
  chunk_id: string;
  text: string;
  score: number;
  title: string;
  context: Array<Record<string, unknown>>;
  source_type?: string;
  metadata?: Record<string, unknown>;
}

export type AiChunk = AiQueryChunk | AiRetrievedChunk;

export type AiTechnology =
  | string
  | {
      name?: string;
      status?: string;
      version?: string;
      [key: string]: unknown;
    };

export type AiTechnologiesResponse = AiTechnology[] | Record<string, unknown>;

export interface AiIngestionResult {
  document_id: string;
  title: string;
  file_type: string;
  source_path: string;
  total_blocks: number;
  total_units: number;
  total_chunks: number;
  chunk_ids: string[];
  filename?: string | null;
  ingestion_version: number;
  chunking_strategy?: string | null;
  total_parent_nodes: number;
  total_child_chunks: number;
  embedded_chunks: number;
  skipped_chunks: number;
  avg_chunk_tokens: number;
  max_chunk_tokens: number;
  warnings: string[];
}

export interface AiKnowledgeQueryResponse {
  query: string;
  answer_preview: string;
  source: string;
  top_k: number;
  chunks: AiQueryChunk[];
}

export interface AiKnowledgeAskResponse {
  answer: string;
  llm_status: string;
  llm_error: string | null;
  chunks: AiQueryChunk[];
}

export interface AiDocumentImportResponse {
  fileName: string;
  fileType: string;
  filePath: string;
  sizeBytes: number;
}

export interface AiDocumentProcessRequest {
  jobId: string;
  documentId: string;
  workspaceId: string;
  userId: string;
  sourceType: "USER_DOCUMENT" | "SYSTEM_KB";
  fileName: string;
  fileType: string;
  filePath: string;
  callbackUrl: string;
}

export interface AiDocumentProcessAcceptedResponse {
  jobId: string;
  documentId: string;
  status: "ACCEPTED";
}

export interface AiRagQueryRequest {
  request_id: string;
  user_id: string;
  workspace_id: string;
  document_id?: string | null;
  question: string;
  top_k_checklist?: number;
  top_k_user_chunks_per_checklist?: number;
  top_k_knowledge_chunks?: number;
}

export interface AiRagChecklistResult {
  checklist_id: string;
  category: string;
  title: string;
  risk_question: string;
  priority: number;
  user_chunks_found: Array<Record<string, unknown>>;
}

export interface AiRagQueryResponse {
  request_id: string;
  success: boolean;
  answer: string | null;
  confidence_score: number | null;
  should_suggest_ticket: boolean;
  suggestion_type: string;
  suggestion_reason: string | null;
  missing_information: string | null;
  checklist_results: AiRagChecklistResult[];
  risk_level: string;
  legal_domain: string | null;
  user_action_hint: string;
  knowledge_chunks: Array<Record<string, unknown>>;
  total_checklist_items: number;
  total_user_chunks: number;
  total_knowledge_chunks: number;
  processing_time_ms: number;
  error_message: string | null;
}

export interface AiLegalQueryRequest {
  request_id?: string | null;
  user_id?: string | null;
  workspace_id?: string | null;
  document_id?: string | null;
  question: string;
  top_k_checklist?: number;
  top_k_user_chunks_per_checklist?: number;
  top_k_knowledge_chunks?: number;
}

export interface AiLegalQueryResponse {
  request_id: string | null;
  success: boolean | null;
  answer: string | null;
  confidence_score: number | null;
  should_suggest_ticket: boolean | null;
  suggestion_type: string | null;
  suggestion_reason: string | null;
  missing_information: string | null;
  risk_level: string | null;
  legal_domain: string | null;
  user_action_hint: string | null;
  usage?: {
    prompt_tokens?: number | null;
    completion_tokens?: number | null;
    total_tokens?: number | null;
  } | null;
  model?: string | null;
  error_message?: string | null;
}

export interface AiLegalBasis {
  source_id: string;
  title: string;
  content: string;
  score: number;
  source_type: string;
  metadata: Record<string, unknown>;
}

export interface AiContractClauseFinding {
  clause_id: string;
  title: string;
  text: string;
  taxonomy: string | null;
  taxonomy_confidence: number;
  risk_concept: string;
  severity: string;
  confidence: number;
  explanation: string;
  detection_method: string;
  llm_used: boolean;
  legal_basis: AiLegalBasis[];
}

export interface AiContractSummary {
  clause_count: number;
  finding_count: number;
  high_risk_count: number;
  medium_risk_count: number;
  low_risk_count: number;
  llm_used_count: number;
}

export interface AiContractAnalysisResponse {
  document_id: string;
  filename: string;
  title: string;
  file_type: string;
  source_path: string;
  supported_formats: string[];
  clauses: AiContractClauseFinding[];
  summary: AiContractSummary;
  knowledge_source_files: string[];
}
