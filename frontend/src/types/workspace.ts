import type { Status } from "./status";
import type { SupportedContractType } from "../config/supportedContractTypes";

export interface Workspace {
  workspaceId: string;
  name: string;
  description: string;
  status: Status;
  createdAt: string;
}

export interface Document {
  documentId: string;
  workspaceId: string;
  originalFileName: string;
  fileType: string;
  fileSize: number;
  status: Status;
  uploadedAt: string;
  errorMessage?: string | null;
  contractType?: SupportedContractType | null;
  contractTypeConfirmed?: boolean;
}

export interface CreateWorkspaceRequest {
  name: string;
  description: string;
}
