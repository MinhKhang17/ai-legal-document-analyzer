import { API_ENDPOINTS } from "../config/api";
import type { AiContractAnalysisResponse } from "../types/ai";
import { requestAiJson } from "./http";

export const getAiContractSupportedFormats = async (): Promise<string[]> =>
  requestAiJson<string[]>(
    API_ENDPOINTS.aiContracts.supportedFormats,
    { method: "GET" },
    "Không thể tải định dạng hợp đồng hỗ trợ",
  );

export const uploadAiContract = async (
  file: File,
  title?: string,
): Promise<AiContractAnalysisResponse> => {
  const formData = new FormData();
  formData.append("file", file);
  if (title?.trim()) {
    formData.append("title", title.trim());
  }

  return requestAiJson<AiContractAnalysisResponse>(
    API_ENDPOINTS.aiContracts.upload,
    {
      method: "POST",
      body: formData,
    },
    "Không thể phân tích hợp đồng trực tiếp bằng AI-service",
  );
};
