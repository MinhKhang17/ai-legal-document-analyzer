import { API_ENDPOINTS } from "../config/api";
import type {
  ArchiveKnowledgeRequest,
  IngestKnowledgeRequest,
  KnowledgeBaseEntry,
  KnowledgeBaseVersion,
  KnowledgeIngestionJob,
  KnowledgeReviewRequest,
  PageResponse,
  PublishKnowledgeRequest,
  UploadKnowledgeRequest,
} from "../types/knowledgeBase";
import { buildAuthHeaders, requestApiData } from "./http";

const jsonHeaders = {
  Accept: "application/json",
  "Content-Type": "application/json",
};

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

export const getKnowledgeBaseEntries = async (
  page = 0,
  size = 10,
): Promise<PageResponse<KnowledgeBaseEntry>> => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return getJson<PageResponse<KnowledgeBaseEntry>>(
    `${API_ENDPOINTS.knowledgeBase.list}?${query.toString()}`,
    "Không thể tải danh sách knowledge base",
  );
};

export const getKnowledgeBaseEntry = async (
  entryId: string,
): Promise<KnowledgeBaseEntry> =>
  getJson<KnowledgeBaseEntry>(
    API_ENDPOINTS.knowledgeBase.detail(entryId),
    "Không thể tải chi tiết knowledge base",
  );

export const uploadKnowledgeBaseEntry = async (
  payload: UploadKnowledgeRequest,
): Promise<KnowledgeBaseVersion> =>
  postJson<KnowledgeBaseVersion>(
    API_ENDPOINTS.knowledgeBase.upload,
    payload,
    "Không thể upload knowledge base",
  );

export const ingestKnowledgeBaseEntry = async (
  entryId: string,
  payload: IngestKnowledgeRequest,
): Promise<KnowledgeIngestionJob> =>
  postJson<KnowledgeIngestionJob>(
    API_ENDPOINTS.knowledgeBase.ingest(entryId),
    payload,
    "Không thể ingest knowledge base",
  );

export const getKnowledgeBaseVersions = async (
  entryId: string,
): Promise<KnowledgeBaseVersion[]> =>
  getJson<KnowledgeBaseVersion[]>(
    API_ENDPOINTS.knowledgeBase.versions(entryId),
    "Không thể tải version knowledge base",
  );

export const reviewKnowledgeBaseEntry = async (
  entryId: string,
  payload: KnowledgeReviewRequest,
): Promise<KnowledgeBaseVersion> =>
  postJson<KnowledgeBaseVersion>(
    API_ENDPOINTS.knowledgeBase.review(entryId),
    payload,
    "Không thể review knowledge base",
  );

export const publishKnowledgeBaseEntry = async (
  entryId: string,
  payload: PublishKnowledgeRequest,
): Promise<KnowledgeBaseVersion> =>
  postJson<KnowledgeBaseVersion>(
    API_ENDPOINTS.knowledgeBase.publish(entryId),
    payload,
    "Không thể publish knowledge base",
  );

export const archiveKnowledgeBaseEntry = async (
  entryId: string,
  payload: ArchiveKnowledgeRequest,
): Promise<KnowledgeBaseVersion> =>
  postJson<KnowledgeBaseVersion>(
    API_ENDPOINTS.knowledgeBase.archive(entryId),
    payload,
    "Không thể archive knowledge base",
  );
