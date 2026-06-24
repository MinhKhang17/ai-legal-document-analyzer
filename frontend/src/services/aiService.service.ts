import { API_ENDPOINTS } from "../config/api";
import type { AiHealthStatus, AiRootStatus, AiTechnologiesResponse } from "../types/ai";
import { requestAiJson } from "./http";

export const getAiServiceRoot = async (): Promise<AiRootStatus> =>
  requestAiJson<AiRootStatus>(
    API_ENDPOINTS.aiService.root,
    { method: "GET" },
    "Không thể tải AI-service root status",
  );

export const getAiServiceHealth = async (): Promise<AiHealthStatus> =>
  requestAiJson<AiHealthStatus>(
    API_ENDPOINTS.aiService.health,
    { method: "GET" },
    "Không thể tải AI-service health",
);

export const getAiTechnologies = async (): Promise<AiTechnologiesResponse> =>
  requestAiJson<AiTechnologiesResponse>(
    API_ENDPOINTS.aiService.technologies,
    { method: "GET" },
    "Không thể tải danh sách technologies từ AI-service",
  );

export const getAiRootStatus = getAiServiceRoot;
export const getAiHealthStatus = getAiServiceHealth;
