export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface UploadKnowledgeRequest {
  code: string;
  title: string;
  category: string;
  scope: "GLOBAL" | "WORKSPACE" | string;
  createdById: number;
  workspaceId?: number | null;
  extractedContent?: string;
  rawContent?: string | null;
  description?: string | null;
}

export interface IngestKnowledgeRequest {
  requestId: string;
  jobPayload?: string | null;
}

export type KnowledgeStatus = "PENDING" | "UPLOADED" | "PROCESSING" | "INGESTED" | "REVIEWING" | "PUBLIC" | "ARCHIVED" | "FAILED";
export type KnowledgeVisibility = "PRIVATE" | "PUBLIC";
export type KnowledgeReviewDecision = "APPROVE" | "REQUEST_CHANGES" | "REJECT";
export type KnowledgeAction = "INGEST" | "REVIEW" | "PUBLISH" | "UNPUBLISH" | "ARCHIVE";

export interface KnowledgeReviewRequest {
  decision: KnowledgeReviewDecision;
  note?: string | null;
}

export interface PublishKnowledgeRequest {
  note: string;
}

export interface ArchiveKnowledgeRequest {
  reason: string;
}

export interface KnowledgeBaseEntry {
  id: string;
  code: string;
  title: string;
  category: string;
  scope: string;
  currentVersionNo: number | null;
  currentStatus: KnowledgeStatus | null;
  active: boolean;
  createdById: number;
  workspaceId: number | null;
  createdAt: string;
  updatedAt: string;
  description?: string | null;
  fileName?: string | null;
  contentType?: string | null;
  size?: number | null;
  uploadedAt?: string | null;
  sourceFileAvailable?: boolean;
}

export interface KnowledgeBaseVersion {
  id: string;
  knowledgeBaseEntryId: string;
  versionNo: number;
  sourceDocumentId: string | null;
  rawContent: string | null;
  extractedContent: string | null;
  status: KnowledgeStatus | null;
  ingestStatus?: KnowledgeStatus | null;
  visibility?: KnowledgeVisibility | null;
  active?: boolean | null;
  ingestedAt?: string | null;
  ingestedById?: number | null;
  errorMessage?: string | null;
  description?: string | null;
  fileName?: string | null;
  contentType?: string | null;
  size?: number | null;
  uploadedAt?: string | null;
  sourceFileAvailable?: boolean;
  reviewDecision: KnowledgeReviewDecision | null;
  reviewedById: number | null;
  reviewedAt: string | null;
  publishedById: number | null;
  publishedAt: string | null;
  archivedById: number | null;
  archivedAt: string | null;
  failedReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeBaseIngestedDocumentVersion {
  versionId: string;
  versionLabel: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  visibility: KnowledgeVisibility | null;
  active?: boolean | null;
  ingestStatus: KnowledgeStatus | null;
  chunkCount: number;
  embeddedCount: number;
  sourceFileId: string | null;
  contentHash: string | null;
  ingestedAt: string | null;
  publishedAt?: string | null;
  ingestedById?: number | null;
  errorMessage?: string | null;
}

export interface KnowledgeBaseIngestedDocument {
  legalDocumentId: string;
  title: string | null;
  documentCode: string | null;
  versions: KnowledgeBaseIngestedDocumentVersion[];
}

export interface KnowledgeIngestionJob {
  id: string;
  knowledgeBaseVersionId: string;
  requestId: string;
  status: KnowledgeStatus;
  jobPayload: string | null;
  errorMessage: string | null;
  progressPercent?: number | null;
  startedAt: string | null;
  completedAt: string | null;
  ingestedById?: number | null;
  createdAt: string;
}

export interface AsyncKnowledgeIngestAccepted {
  jobId: string;
  status: "PROCESSING";
  progressPercent: number;
}
