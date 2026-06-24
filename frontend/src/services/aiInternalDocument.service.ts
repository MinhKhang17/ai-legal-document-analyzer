import { API_ENDPOINTS } from "../config/api";
import type {
  AiDocumentImportResponse,
  AiDocumentProcessAcceptedResponse,
  AiDocumentProcessRequest,
} from "../types/ai";
import { requestAiJson } from "./http";

export const importAiInternalDocument = async (file: File): Promise<AiDocumentImportResponse> => {
  const formData = new FormData();
  formData.append("file", file);

  return requestAiJson<AiDocumentImportResponse>(
    API_ENDPOINTS.aiInternalDocuments.import,
    {
      method: "POST",
      body: formData,
    },
    "Không thể import internal document vào AI-service",
  );
};

export const processAiInternalDocument = async (
  payload: AiDocumentProcessRequest,
): Promise<AiDocumentProcessAcceptedResponse> =>
  requestAiJson<AiDocumentProcessAcceptedResponse>(
    API_ENDPOINTS.aiInternalDocuments.process,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "Không thể process internal document bằng AI-service",
  );
