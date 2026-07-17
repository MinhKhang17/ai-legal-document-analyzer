import { API_ENDPOINTS } from "../config/api";
import type {
  AiCitation,
  AiFeatureSummary,
  AiRiskAssessment,
} from "../types/aiFeature";
import { buildAuthHeaders, requestApiData } from "./http";

const requestAiFeature = <TResponse>(
  endpoint: string,
  errorMessage: string,
): Promise<TResponse> =>
  requestApiData<TResponse>(
    endpoint,
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    errorMessage,
  );

export const getTicketAiAssessment = async (
  ticketId: string,
): Promise<AiRiskAssessment> =>
  requestAiFeature<AiRiskAssessment>(
    API_ENDPOINTS.aiLegal.ticketAssessment(ticketId),
    "Không thể tải đánh giá AI của ticket",
  );

export const getTicketAiSummary = async (
  ticketId: string,
): Promise<AiFeatureSummary> =>
  requestAiFeature<AiFeatureSummary>(
    API_ENDPOINTS.aiLegal.ticketSummary(ticketId),
    "Không thể tải tóm tắt AI của ticket",
  );

export const getTicketAiCitations = async (
  ticketId: string,
): Promise<AiCitation[]> =>
  requestAiFeature<AiCitation[]>(
    API_ENDPOINTS.aiLegal.ticketCitations(ticketId),
    "Không thể tải citation của ticket",
  );

export const getChatMessageAiCitations = async (
  chatMessageId: string,
): Promise<AiCitation[]> =>
  requestAiFeature<AiCitation[]>(
    API_ENDPOINTS.aiLegal.chatCitations(chatMessageId),
    "Không thể tải citation của tin nhắn chat",
  );
