import { API_ENDPOINTS } from "../config/api";
import type {
  AiIngestionResult,
  AiKnowledgeAskResponse,
  AiKnowledgeQueryResponse,
  AiRetrievedChunk,
} from "../types/ai";
import { requestAiJson } from "./http";

export interface AiKnowledgeQueryRequest {
  query: string;
  top_k?: number;
}

const jsonHeaders = {
  Accept: "application/json",
  "Content-Type": "application/json",
};

const uploadKnowledge = async (
  endpointPath: string,
  file: File,
  title?: string,
): Promise<AiIngestionResult> => {
  const formData = new FormData();
  formData.append("file", file);
  if (title?.trim()) {
    formData.append("title", title.trim());
  }

  return requestAiJson<AiIngestionResult>(
    endpointPath,
    {
      method: "POST",
      body: formData,
    },
    "Không thể import knowledge document",
  );
};

export const getKnowledgeSupportedFormats = async (): Promise<string[]> =>
  requestAiJson<string[]>(
    API_ENDPOINTS.aiKnowledge.supportedFormats,
    { method: "GET" },
    "Không thể tải supported formats knowledge",
  );

export const ingestKnowledgeDocument = async (
  file: File,
  title?: string,
): Promise<AiIngestionResult> =>
  uploadKnowledge(API_ENDPOINTS.aiKnowledge.ingest, file, title);

export const ingestKnowledgeDocumentV2 = async (
  file: File,
  title?: string,
): Promise<AiIngestionResult> =>
  uploadKnowledge(API_ENDPOINTS.aiKnowledge.ingestV2, file, title);

export const searchKnowledge = async (
  query: string,
  topK = 5,
): Promise<AiRetrievedChunk[]> =>
  requestAiJson<AiRetrievedChunk[]>(
    API_ENDPOINTS.aiKnowledge.search,
    {
      method: "POST",
      headers: jsonHeaders,
      body: JSON.stringify({ query, top_k: topK }),
    },
    "Không thể search knowledge",
  );

export const askKnowledge = async (
  query: string,
  topK = 5,
): Promise<AiKnowledgeQueryResponse> =>
  requestAiJson<AiKnowledgeQueryResponse>(
    API_ENDPOINTS.aiKnowledge.ask,
    {
      method: "POST",
      headers: jsonHeaders,
      body: JSON.stringify({ query, top_k: topK }),
    },
    "Không thể hỏi knowledge API",
  );

export const queryKnowledgeV2 = async (
  query: string,
  topK = 5,
): Promise<AiKnowledgeAskResponse> =>
  requestAiJson<AiKnowledgeAskResponse>(
    API_ENDPOINTS.aiKnowledge.queryV2,
    {
      method: "POST",
      headers: jsonHeaders,
      body: JSON.stringify({ query, top_k: topK }),
    },
    "Không thể hỏi knowledge v2 API",
  );

export const getAiKnowledgeSupportedFormats = getKnowledgeSupportedFormats;
export const ingestAiKnowledgeDocument = ingestKnowledgeDocument;
export const ingestAiKnowledgeDocumentV2 = ingestKnowledgeDocumentV2;

export const searchAiKnowledge = async ({
  query,
  top_k = 5,
}: AiKnowledgeQueryRequest): Promise<AiRetrievedChunk[]> =>
  searchKnowledge(query, top_k);

export const askAiKnowledge = async ({
  query,
  top_k = 5,
}: AiKnowledgeQueryRequest): Promise<AiKnowledgeQueryResponse> =>
  askKnowledge(query, top_k);

export const queryAiKnowledgeV2 = async ({
  query,
  top_k = 5,
}: AiKnowledgeQueryRequest): Promise<AiKnowledgeAskResponse> =>
  queryKnowledgeV2(query, top_k);
