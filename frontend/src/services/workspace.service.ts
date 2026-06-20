import type { Document } from "../types/workspace";
import { API_ENDPOINTS, buildApiUrl } from "../config/api";

export type WorkspaceResponse = {
  workspaceId: string;
  name: string;
  description: string;
  status: string;
  createdAt: string;
};

export type DocumentResponse = {
  documentId: string;
  workspaceId: string;
  originalFileName: string;
  fileType: string;
  fileSize: number;
  status: "PROCESSING" | "READY" | "FAILED";
  uploadedAt: string;
};

const getAuthHeaders = (accessToken: string): HeadersInit => ({
  Authorization: `Bearer ${accessToken}`,
});

const normalizeStatus = (status: string) => {
  return status.toLowerCase() as Document['status'];
};

export async function createWorkspace(
  accessToken: string,
  payload: {
    name: string;
    description: string;
  },
): Promise<WorkspaceResponse> {
  const response = await fetch(buildApiUrl(API_ENDPOINTS.workspaces.create), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(accessToken),
    },
    credentials: "include",
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error("Tạo workspace thất bại");
  }

  return response.json();
}

export async function uploadDocument(
  accessToken: string,
  workspaceId: string,
  file: File,
): Promise<Document> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.workspaces.documents(workspaceId)),
    {
      method: "POST",
      headers: getAuthHeaders(accessToken),
      credentials: "include",
      body: formData,
    },
  );

  if (!response.ok) {
    throw new Error("Upload tài liệu thất bại");
  }

  const data: DocumentResponse = await response.json();

  return {
    ...data,
    status: normalizeStatus(data.status),
  };
}

export async function getWorkspaceDocuments(
  accessToken: string,
  workspaceId: string,
): Promise<Document[]> {
  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.workspaces.documents(workspaceId)),
    {
      method: "GET",
      headers: getAuthHeaders(accessToken),
      credentials: "include",
    },
  );

  if (!response.ok) {
    throw new Error("Không thể tải danh sách tài liệu");
  }

  const data: DocumentResponse[] = await response.json();

  return data.map((document) => ({
    ...document,
    status: normalizeStatus(document.status),
  }));
}