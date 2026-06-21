import type { Status } from "./status";

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
}

export interface CreateWorkspaceRequest {
  name: string;
  description: string;
}