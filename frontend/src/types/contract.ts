export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type ContractTemplateStatus = "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type ContractGenerationStatus = "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";
export type ContractStatus = "DRAFT" | "GENERATED" | "ACTIVE" | "ARCHIVED";

export interface ContractTemplate {
  id: number;
  templateCode: string;
  name: string;
  description?: string | null;
  category: string;
  jurisdiction?: string | null;
  content: string;
  status: ContractTemplateStatus;
  version: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateContractTemplateRequest {
  templateCode: string;
  name: string;
  description?: string | null;
  category: string;
  jurisdiction?: string | null;
  content: string;
}

export interface UpdateContractTemplateRequest {
  name: string;
  description?: string | null;
  category: string;
  jurisdiction?: string | null;
  content: string;
}

export interface GenerateContractRequest {
  requestId: string;
  /**
   * BLOCKED_BY_BACKEND: contract endpoints currently require numeric workspaceId
   * while workspace APIs return ids such as "ws_...".
   */
  workspaceId: number;
  templateId?: number | null;
  sourceDocumentId?: string | null;
  inputJson: string;
}

export interface ContractGenerationJob {
  id: string;
  requestId: string;
  requesterId: number;
  workspaceId: number;
  templateId?: number | null;
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
  /**
   * BLOCKED_BY_BACKEND: contract endpoints currently require numeric workspaceId
   * while workspace APIs return ids such as "ws_...".
   */
  workspaceId: number;
  templateId?: number | null;
  generationJobId?: string | null;
  sourceDocumentId?: string | null;
  title: string;
  contractType: string;
  content: string;
}

export interface UserContract {
  id: string;
  ownerId: number;
  workspaceId: number;
  templateId?: number | null;
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
