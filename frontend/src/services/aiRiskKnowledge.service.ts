import { API_ENDPOINTS } from "../config/api";
import type { AiIngestionResult, AiKnowledgeQueryResponse } from "../types/ai";
import { requestAiJson } from "./http";

export interface AiRiskKnowledgeQueryRequest {
  query: string;
  top_k?: number;
}

const buildUploadFormData = (file: File, title?: string) => {
  const formData = new FormData();
  formData.append("file", file);
  if (title?.trim()) {
    formData.append("title", title.trim());
  }
  return formData;
};

export const getAiRiskKnowledgeSupportedFormats = async (): Promise<string[]> =>
  requestAiJson<string[]>(
    API_ENDPOINTS.aiRiskKnowledge.supportedFormats,
    { method: "GET" },
    "Không thể tải định dạng risk knowledge hỗ trợ",
  );

export const importAiRiskKnowledgeDocument = async (
  file: File,
  title?: string,
): Promise<AiIngestionResult> =>
  requestAiJson<AiIngestionResult>(
    API_ENDPOINTS.aiRiskKnowledge.import,
    {
      method: "POST",
      body: buildUploadFormData(file, title),
    },
    "Không thể import risk knowledge",
  );

export const importAiRiskKnowledgeDocumentV2 = async (
  file: File,
  title?: string,
): Promise<AiIngestionResult> =>
  requestAiJson<AiIngestionResult>(
    API_ENDPOINTS.aiRiskKnowledge.importV2,
    {
      method: "POST",
      body: buildUploadFormData(file, title),
    },
    "Không thể import risk knowledge v2",
  );

export const queryAiRiskKnowledge = async (
  payload: AiRiskKnowledgeQueryRequest,
): Promise<AiKnowledgeQueryResponse> =>
  requestAiJson<AiKnowledgeQueryResponse>(
    API_ENDPOINTS.aiRiskKnowledge.query,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "Không thể query risk knowledge",
  );

export const queryAiRiskKnowledgeV2 = async (
  payload: AiRiskKnowledgeQueryRequest,
): Promise<AiKnowledgeQueryResponse> =>
  requestAiJson<AiKnowledgeQueryResponse>(
    API_ENDPOINTS.aiRiskKnowledge.queryV2,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "Không thể query risk knowledge v2",
  );
