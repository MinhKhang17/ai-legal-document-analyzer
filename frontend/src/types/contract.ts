export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type ContractGenerationStatus = "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";
export type ContractStatus = "DRAFT" | "GENERATED" | "ACTIVE" | "ARCHIVED";

export interface GenerateContractRequest {
  requestId: string;
  workspaceId: string;
  sourceDocumentId?: string | null;
  inputJson: string;
}

export interface ContractGenerationJob {
  id: string;
  requestId: string;
  requesterId: number;
  workspaceId: string;
  sourceDocumentId?: string | null;
  inputJson: string;
  promptSnapshot?: string | null;
  outputDraft?: string | null;
  status: ContractGenerationStatus;
  errorMessage?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface SaveContractRequest {
  workspaceId: string;
  generationJobId?: string | null;
  sourceDocumentId?: string | null;
  title: string;
  contractType: string;
  content: string;
}

export interface UserContract {
  id: string;
  ownerId: number;
  workspaceId: string;
  generationJobId?: string | null;
  sourceDocumentId?: string | null;
  title: string;
  contractType: string;
  status: ContractStatus;
  currentVersionNo: number;
  currentContentHash?: string | null;
  lastGeneratedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface ContractVersion {
  id: string;
  contractId: string;
  versionNo: number;
  content: string;
  changeSummary?: string | null;
  generatedById?: number | null;
  generatedByAi: boolean;
  generationJobId?: string | null;
  createdAt?: string | null;
}

export interface RevertContractVersionRequest {
  reason: string;
}
