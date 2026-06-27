import { API_ENDPOINTS } from "../config/api";
import type { AiLegalQueryRequest, AiLegalQueryResponse } from "../types/ai";
import { buildAuthHeaders, requestApiData } from "./http";

export const queryAiLegal = async (
  payload: AiLegalQueryRequest,
): Promise<AiLegalQueryResponse> =>
  requestApiData<AiLegalQueryResponse>(
    API_ENDPOINTS.aiLegal.query,
    {
      method: "POST",
      headers: buildAuthHeaders({
        Accept: "application/json",
        "Content-Type": "application/json",
      }),
      credentials: "include",
      body: JSON.stringify(payload),
    },
    "Không thể gọi backend AI legal query",
  );
