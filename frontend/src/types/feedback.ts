export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type FeedbackSurveyType = "SATISFACTION" | "PRODUCT" | "BUG" | "USABILITY";
export type FeedbackSurveyStatus = "DRAFT" | "ACTIVE" | "CLOSED" | "ARCHIVED";
export type AiReportStatus = "OPEN" | "UNDER_REVIEW" | "RESOLVED" | "REJECTED";

export interface FeedbackSurvey {
  id: string;
  code: string;
  title: string;
  description?: string | null;
  surveyType: FeedbackSurveyType;
  status: FeedbackSurveyStatus;
  targetType: string;
  createdById: number;
  workspaceId?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateSurveyRequest {
  code: string;
  title: string;
  description?: string | null;
  surveyType: FeedbackSurveyType;
  targetType: string;
  createdById: number;
  workspaceId?: number | null;
}

export interface UpdateSurveyRequest {
  title: string;
  description?: string | null;
  surveyType: FeedbackSurveyType;
  targetType: string;
  status: FeedbackSurveyStatus;
}

export interface SubmitSurveyResponseRequest {
  respondentId: number;
  sourceReferenceId?: string | null;
  rating?: number | null;
  answerJson?: string | null;
  comment?: string | null;
}

export interface AiReportCreateRequest {
  reportType: string;
  sourceType: string;
  sourceReferenceId: string;
  summary: string;
  detailsJson?: string | null;
  submittedById: number;
  workspaceId?: number | null;
}

export interface AiReport {
  id: string;
  reportType: string;
  sourceType: string;
  sourceReferenceId: string;
  summary: string;
  detailsJson?: string | null;
  submittedById?: number | null;
  workspaceId?: number | null;
  status: AiReportStatus;
  createdAt?: string | null;
  updatedAt?: string | null;
}
