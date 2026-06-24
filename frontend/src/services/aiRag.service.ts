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

export const runAiRagTestQuery = async (
  question: string,
  userId: string,
): Promise<AiRagQueryResponse> => {
  const query = new URLSearchParams({ question, user_id: userId });
  return requestAiJson<AiRagQueryResponse>(
    `${API_ENDPOINTS.aiRag.testQuery}?${query.toString()}`,
    { method: "GET" },
    "Không thể chạy AI RAG test query",
  );
};

export const queryInternalRagDirect = queryAiRagInternal;
export const runAiTestQuery = runAiRagTestQuery;
