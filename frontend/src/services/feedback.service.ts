import { API_ENDPOINTS } from "../config/api";
import type {
  AiReport,
  AiReportCreateRequest,
  CreateSurveyRequest,
  FeedbackSurvey,
  PageResponse,
  SubmitSurveyResponseRequest,
  UpdateSurveyRequest,
} from "../types/feedback";
import { buildAuthHeaders, requestApiData } from "./http";

const jsonHeaders = {
  Accept: "application/json",
  "Content-Type": "application/json",
};

const buildPageQuery = (page: number, size: number) =>
  new URLSearchParams({ page: String(page), size: String(size) }).toString();

const getJson = <TResponse>(endpoint: string, errorMessage: string) =>
  requestApiData<TResponse>(
    endpoint,
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    errorMessage,
  );

const postJson = <TResponse>(
  endpoint: string,
  payload: object,
  errorMessage: string,
) =>
  requestApiData<TResponse>(
    endpoint,
    {
      method: "POST",
      headers: buildAuthHeaders(jsonHeaders),
      credentials: "include",
      body: JSON.stringify(payload),
    },
    errorMessage,
  );

const putJson = <TResponse>(
  endpoint: string,
  payload: object,
  errorMessage: string,
) =>
  requestApiData<TResponse>(
    endpoint,
    {
      method: "PUT",
      headers: buildAuthHeaders(jsonHeaders),
      credentials: "include",
      body: JSON.stringify(payload),
    },
    errorMessage,
  );

export const getAdminFeedbackSurveys = async (
  page = 0,
  size = 20,
): Promise<PageResponse<FeedbackSurvey>> =>
  getJson<PageResponse<FeedbackSurvey>>(
    `${API_ENDPOINTS.feedback.adminSurveys}?${buildPageQuery(page, size)}`,
    "Không thể tải danh sách feedback survey",
  );

export const createFeedbackSurvey = async (
  payload: CreateSurveyRequest,
): Promise<FeedbackSurvey> =>
  postJson<FeedbackSurvey>(
    API_ENDPOINTS.feedback.adminSurveys,
    payload,
    "Không thể tạo feedback survey",
  );

export const updateFeedbackSurvey = async (
  surveyId: string,
  payload: UpdateSurveyRequest,
): Promise<FeedbackSurvey> =>
  putJson<FeedbackSurvey>(
    API_ENDPOINTS.feedback.adminSurveyDetail(surveyId),
    payload,
    "Không thể cập nhật feedback survey",
  );

export const submitFeedbackSurveyResponse = async (
  surveyId: string,
  payload: SubmitSurveyResponseRequest,
): Promise<FeedbackSurvey> =>
  postJson<FeedbackSurvey>(
    API_ENDPOINTS.feedback.surveyResponses(surveyId),
    payload,
    "Không thể gửi phản hồi survey",
  );

export const createAiFeedbackReport = async (
  payload: AiReportCreateRequest,
): Promise<AiReport> =>
  postJson<AiReport>(
    API_ENDPOINTS.feedback.aiReports,
    payload,
    "Không thể gửi AI feedback report",
  );

export const getAdminAiFeedbackReports = async (
  page = 0,
  size = 20,
): Promise<PageResponse<AiReport>> =>
  getJson<PageResponse<AiReport>>(
    `${API_ENDPOINTS.feedback.adminAiReports}?${buildPageQuery(page, size)}`,
    "Không thể tải danh sách AI feedback report",
  );

export const getAdminAiFeedbackReport = async (
  reportId: string,
): Promise<AiReport> =>
  getJson<AiReport>(
    API_ENDPOINTS.feedback.adminAiReportDetail(reportId),
    "Không thể tải chi tiết AI feedback report",
  );
