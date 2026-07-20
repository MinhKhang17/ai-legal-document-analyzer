import { API_ENDPOINTS, buildApiUrl } from "../config/api";
import { buildBearerHeaders, requestApiData } from "./http";
import type {
  CreateWorkspaceRequest,
  Document,
  Workspace,
} from "../types/workspace";

// interface ApiResponse<T> {
//   code: number;
//   message: string;
//   data: T;
// }

type WorkspaceResponse = Omit<Workspace, "status"> & {
  status: string;
};

type DocumentResponse = Omit<Document, "status"> & {
  status: string;
};

const normalizeStatus = <T extends { status: string }>(item: T) => ({
  ...item,
  status: item.status.toLowerCase(),
});

export async function getWorkspaces(accessToken: string): Promise<Workspace[]> {
  const data = await requestApiData<WorkspaceResponse[]>(
    API_ENDPOINTS.workspaces.list,
    {
      method: "GET",
      headers: buildBearerHeaders(accessToken),
      credentials: "include",
    },
    "Không thể tải danh sách workspace",
  );

  return data.map((workspace) => normalizeStatus(workspace) as Workspace);
}

export async function getWorkspaceDetail(
  accessToken: string,
  workspaceId: string,
): Promise<Workspace> {
  const data = await requestApiData<WorkspaceResponse>(
    API_ENDPOINTS.workspaces.detail(workspaceId),
    {
      method: "GET",
      headers: buildBearerHeaders(accessToken),
      credentials: "include",
    },
    "Không thể tải workspace",
  );

  return normalizeStatus(data) as Workspace;
}

export async function createWorkspace(
  accessToken: string,
  payload: CreateWorkspaceRequest,
): Promise<Workspace> {
  const data = await requestApiData<WorkspaceResponse>(
    API_ENDPOINTS.workspaces.create,
    {
      method: "POST",
      headers: buildBearerHeaders(accessToken, {
        "Content-Type": "application/json",
      }),
      credentials: "include",
      body: JSON.stringify(payload),
    },
    "Tạo workspace thất bại",
  );

  return normalizeStatus(data) as Workspace;
}

export async function uploadDocument(
  accessToken: string,
  workspaceId: string,
  file: File,
): Promise<Document> {
  const formData = new FormData();
  formData.append("file", file);

  const data = await requestApiData<DocumentResponse>(
    API_ENDPOINTS.workspaces.documents(workspaceId),
    {
      method: "POST",
      headers: buildBearerHeaders(accessToken),
      credentials: "include",
      body: formData,
    },
    "Upload tài liệu thất bại",
  );

  return normalizeStatus(data) as Document;
}

export async function getWorkspaceDocuments(
  accessToken: string,
  workspaceId: string,
): Promise<Document[]> {
  const data = await requestApiData<DocumentResponse[]>(
    API_ENDPOINTS.workspaces.documents(workspaceId),
    {
      method: "GET",
      headers: buildBearerHeaders(accessToken),
      credentials: "include",
    },
    "Không thể tải danh sách tài liệu",
  );

  return data.map((document) => normalizeStatus(document) as Document);
}


export async function downloadWorkspaceDocument(accessToken: string, workspaceId: string, documentId: string): Promise<string> {
  const response = await fetch(buildApiUrl(API_ENDPOINTS.workspaces.documentDownload(workspaceId, documentId)), {
    method: "GET",
    headers: buildBearerHeaders(accessToken),
    credentials: "include",
  });
  if (!response.ok) throw new Error("Khong the tai tai lieu");
  return URL.createObjectURL(await response.blob());
}
