import { API_ENDPOINTS } from "../config/api";
import type { AiContractAnalysisResponse } from "../types/ai";
import { requestAiJson } from "./http";
import type { SupportedContractType } from "../config/supportedContractTypes";

export const getContractSupportedFormats = async (): Promise<string[]> =>
  requestAiJson<string[]>(
    API_ENDPOINTS.aiContracts.supportedFormats,
    { method: "GET" },
    "Không thể tải supported formats contract analysis",
  );

export const uploadContractForAnalysis = async (
  file: File,
  contractType: SupportedContractType,
  title?: string,
): Promise<AiContractAnalysisResponse> => {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("contract_type", contractType);
  if (title?.trim()) {
    formData.append("title", title.trim());
  }

  return requestAiJson<AiContractAnalysisResponse>(
    API_ENDPOINTS.aiContracts.upload,
    {
      method: "POST",
      body: formData,
    },
    "Không thể phân tích contract trực tiếp qua AI-service",
  );
};
