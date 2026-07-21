import axios, { AxiosProgressEvent, CancelTokenSource } from "axios";
import { API_BASE_URL, API_ENDPOINTS } from "../config/api";
import type { ApiResponse } from "./http";
import type { Document } from "../types/workspace";

export interface UploadProgressCallback {
  (progress: number): void;
}

export async function uploadDocumentAxios(
  accessToken: string,
  workspaceId: string,
  file: File,
  onProgress?: UploadProgressCallback,
  cancelTokenSource?: CancelTokenSource
): Promise<Document> {
  const formData = new FormData();
  formData.append("file", file);

  const endpoint = API_ENDPOINTS.workspaces.documents(workspaceId);
  const url = `${API_BASE_URL.replace(/\/+$/, "")}/${endpoint.replace(/^\/+/, "")}`;

  const response = await axios.post<ApiResponse<Document>>(url, formData, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "multipart/form-data",
    },
    onUploadProgress: (progressEvent: AxiosProgressEvent) => {
      if (progressEvent.total) {
        const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
        onProgress?.(percent);
      }
    },
    cancelToken: cancelTokenSource?.token,
    withCredentials: true,
  });

  const body = response.data;
  if (!body.data) {
    throw new Error(body.message || "Upload tài liệu thất bại");
  }

  // Normalize status to lowercase like in workspace.service.ts
  const doc = body.data;
  return {
    ...doc,
    status: doc.status.toLowerCase() as any,
  };
}
