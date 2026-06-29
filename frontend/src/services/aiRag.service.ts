import { API_ENDPOINTS } from "../config/api";
import type { AiRagQueryRequest, AiRagQueryResponse } from "../types/ai";
import { requestAiJson } from "./http";

export const queryAiRagInternal = async (
  payload: AiRagQueryRequest,
): Promise<AiRagQueryResponse> =>
  requestAiJson<AiRagQueryResponse>(
    API_ENDPOINTS.aiRag.internalQuery,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "Không thể query AI RAG internal",
  );

export const previewAiRagInternal = async (
  payload: AiRagQueryRequest,
): Promise<AiRagQueryResponse> =>
  requestAiJson<AiRagQueryResponse>(
    API_ENDPOINTS.aiRag.preview,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "Không thể preview AI RAG internal",
  );

export const queryInternalRagDirect = queryAiRagInternal;
export const previewInternalRagDirect = previewAiRagInternal;
