import { API_ENDPOINTS, buildApiUrl } from "../config/api";
import type {
  CreateWorkspaceRequest,
  Document,
  Workspace,
} from "../types/workspace";

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

type WorkspaceResponse = Omit<Workspace, "status"> & {
  status: string;
};

type DocumentResponse = Omit<Document, "status"> & {
  status: string;
};

const getAuthHeaders = (accessToken: string): HeadersInit => ({
  Authorization: `Bearer ${accessToken}`,
});

const normalizeStatus = <T extends { status: string }>(item: T) => ({
  ...item,
  status: item.status.toLowerCase(),
});

export async function getWorkspaces(accessToken: string): Promise<Workspace[]> {
  const response = await fetch(buildApiUrl(API_ENDPOINTS.workspaces.list), {
    method: "GET",
    headers: getAuthHeaders(accessToken),
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error("Không thể tải danh sách workspace");
  }

  const json: ApiResponse<WorkspaceResponse[]> = await response.json();
  const data = json.data;

  return data.map((workspace) => normalizeStatus(workspace) as Workspace);
}

export async function getWorkspaceDetail(
  accessToken: string,
  workspaceId: string,
): Promise<Workspace> {
  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.workspaces.detail(workspaceId)),
    {
      method: "GET",
      headers: getAuthHeaders(accessToken),
      credentials: "include",
    },
  );

  if (!response.ok) {
    throw new Error("Không thể tải workspace");
  }

  const json: ApiResponse<WorkspaceResponse> = await response.json();
  const data = json.data;

  return normalizeStatus(data) as Workspace;
}

export async function createWorkspace(
  accessToken: string,
  payload: CreateWorkspaceRequest,
): Promise<Workspace> {
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

  const json: ApiResponse<WorkspaceResponse> = await response.json();
  const data = json.data;

  return normalizeStatus(data) as Workspace;
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

  const json: ApiResponse<DocumentResponse> = await response.json();
  const data = json.data;

  return normalizeStatus(data) as Document;
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

  const json: ApiResponse<DocumentResponse[]> = await response.json();
  const data = json.data;

  return data.map((document) => normalizeStatus(document) as Document);
}