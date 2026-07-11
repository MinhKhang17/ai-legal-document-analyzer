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
  extractedContent: string;
  rawContent?: string | null;
}

export interface IngestKnowledgeRequest {
  requestId: string;
  jobPayload?: string | null;
}

export type KnowledgeStatus = "UPLOADED" | "PROCESSING" | "INGESTED" | "REVIEWING" | "PUBLIC" | "ARCHIVED" | "FAILED";
export type KnowledgeReviewDecision = "APPROVE" | "REQUEST_CHANGES" | "REJECT";
export type KnowledgeAction = "INGEST" | "REVIEW" | "PUBLISH" | "ARCHIVE";

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
}

export interface KnowledgeBaseVersion {
  id: string;
  knowledgeBaseEntryId: string;
  versionNo: number;
  sourceDocumentId: string | null;
  rawContent: string | null;
  extractedContent: string | null;
  status: KnowledgeStatus | null;
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
  visibility: string | null;
  ingestStatus: string | null;
  chunkCount: number;
  embeddedCount: number;
  sourceFileId: string | null;
  contentHash: string | null;
  ingestedAt: string | null;
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
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}
