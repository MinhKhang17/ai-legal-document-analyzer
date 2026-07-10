import { API_ENDPOINTS } from "../config/api";
import type {
  ArchiveKnowledgeRequest,
  IngestKnowledgeRequest,
  KnowledgeBaseEntry,
  KnowledgeBaseIngestedDocument,
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
    "KhÃ´ng thá»ƒ táº£i danh sÃ¡ch knowledge base",
  );
};

export const getKnowledgeBaseEntry = async (
  entryId: string,
): Promise<KnowledgeBaseEntry> =>
  getJson<KnowledgeBaseEntry>(
    API_ENDPOINTS.knowledgeBase.detail(entryId),
    "KhÃ´ng thá»ƒ táº£i chi tiáº¿t knowledge base",
  );

export const getKnowledgeBaseIngestedDocuments = async (
  entryId: string,
  params: {
    keyword?: string;
    ingestStatus?: string;
    visibility?: string;
    page?: number;
    size?: number;
  } = {},
): Promise<PageResponse<KnowledgeBaseIngestedDocument>> => {
  const query = new URLSearchParams();

  if (typeof params.keyword === "string" && params.keyword.trim().length > 0) {
    query.set("keyword", params.keyword.trim());
  }
  if (typeof params.ingestStatus === "string" && params.ingestStatus.trim().length > 0) {
    query.set("ingestStatus", params.ingestStatus.trim());
  }
  if (typeof params.visibility === "string" && params.visibility.trim().length > 0) {
    query.set("visibility", params.visibility.trim());
  }

  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 10));

  return getJson<PageResponse<KnowledgeBaseIngestedDocument>>(
    `${API_ENDPOINTS.knowledgeBase.ingestedDocuments(entryId)}?${query.toString()}`,
    "KhÃ´ng thá»ƒ táº£i danh sÃ¡ch tÃ i liá»‡u Ä‘Ã£ ingest",
  );
};

export const uploadKnowledgeBaseEntry = async (
  payload: UploadKnowledgeRequest,
): Promise<KnowledgeBaseVersion> =>
  postJson<KnowledgeBaseVersion>(
    API_ENDPOINTS.knowledgeBase.upload,
    payload,
    "KhÃ´ng thá»ƒ upload knowledge base",
  );

export const ingestKnowledgeBaseEntry = async (
  entryId: string,
  payload: IngestKnowledgeRequest,
): Promise<KnowledgeIngestionJob> =>
  postJson<KnowledgeIngestionJob>(
    API_ENDPOINTS.knowledgeBase.ingest(entryId),
    payload,
    "KhÃ´ng thá»ƒ ingest knowledge base",
  );

export const getKnowledgeBaseVersions = async (
  entryId: string,
): Promise<KnowledgeBaseVersion[]> =>
  getJson<KnowledgeBaseVersion[]>(
    API_ENDPOINTS.knowledgeBase.versions(entryId),
    "KhÃ´ng thá»ƒ táº£i version knowledge base",
  );

export const reviewKnowledgeBaseEntry = async (
  entryId: string,
  payload: KnowledgeReviewRequest,
): Promise<KnowledgeBaseVersion> =>
  postJson<KnowledgeBaseVersion>(
    API_ENDPOINTS.knowledgeBase.review(entryId),
    payload,
    "KhÃ´ng thá»ƒ review knowledge base",
  );

export const publishKnowledgeBaseEntry = async (
  entryId: string,
  payload: PublishKnowledgeRequest,
): Promise<KnowledgeBaseVersion> =>
  postJson<KnowledgeBaseVersion>(
    API_ENDPOINTS.knowledgeBase.publish(entryId),
    payload,
    "KhÃ´ng thá»ƒ publish knowledge base",
  );

export const archiveKnowledgeBaseEntry = async (
  entryId: string,
  payload: ArchiveKnowledgeRequest,
): Promise<KnowledgeBaseVersion> =>
  postJson<KnowledgeBaseVersion>(
    API_ENDPOINTS.knowledgeBase.archive(entryId),
    payload,
    "KhÃ´ng thá»ƒ archive knowledge base",
  );
