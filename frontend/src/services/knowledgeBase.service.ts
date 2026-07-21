import { API_ENDPOINTS, buildApiUrl } from "../config/api";
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

export interface KnowledgeBaseListFilters {
  keyword?: string;
  status?: string;
  scope?: string;
  category?: string;
  active?: boolean;
  sort?: string[];
}

export const getKnowledgeBaseEntries = async (
  page = 0,
  size = 10,
  filters: KnowledgeBaseListFilters = {},
): Promise<PageResponse<KnowledgeBaseEntry>> => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (filters.keyword?.trim()) query.set("q", filters.keyword.trim());
  if (filters.status) query.set("status", filters.status);
  if (filters.scope) query.set("scope", filters.scope);
  if (filters.category) query.set("category", filters.category);
  if (typeof filters.active === "boolean") query.set("active", String(filters.active));
  filters.sort?.filter(Boolean).forEach((sort) => query.append("sort", sort));
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
    "Không thể tải danh sách tài liệu đã ingest",
  );
};

export const uploadKnowledgeBaseEntry = async (
  payload: UploadKnowledgeRequest,
): Promise<KnowledgeBaseVersion> =>
  postJson<KnowledgeBaseVersion>(
    API_ENDPOINTS.knowledgeBase.upload,
    payload,
    "Không thể upload knowledge base",
  );

export const uploadKnowledgeBaseSourceFile = async (entryId: string, file: File): Promise<KnowledgeBaseVersion> => {
  const formData = new FormData();
  formData.append("file", file);
  return requestApiData<KnowledgeBaseVersion>(
    API_ENDPOINTS.knowledgeBase.sourceFile(entryId),
    { method: "POST", headers: buildAuthHeaders({ Accept: "application/json" }), credentials: "include", body: formData },
    "Không thể lưu file gốc knowledge base",
  );
};

export const downloadKnowledgeBaseSourceFile = async (entryId: string): Promise<void> => {
  const response = await fetch(buildApiUrl(API_ENDPOINTS.knowledgeBase.sourceFile(entryId)), {
    headers: buildAuthHeaders(), credentials: "include",
  });
  if (!response.ok) throw new Error("Không thể tải file gốc knowledge base");
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = "knowledge-source";
  anchor.click();
  URL.revokeObjectURL(url);
};

export const getKnowledgeBaseSourceFile = async (entryId: string): Promise<File> => {
  const response = await fetch(buildApiUrl(API_ENDPOINTS.knowledgeBase.sourceFile(entryId)), {
    headers: buildAuthHeaders(), credentials: "include",
  });
  if (!response.ok) throw new Error("Không thể tải file gốc knowledge base");
  const blob = await response.blob();
  const disposition = response.headers.get("content-disposition") ?? "";
  const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const plainName = disposition.match(/filename="?([^";]+)"?/i)?.[1];
  const fileName = encodedName ? decodeURIComponent(encodedName) : plainName || "knowledge-source";
  return new File([blob], fileName, { type: blob.type || "application/octet-stream" });
};

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

export const getKnowledgeIngestionJob = async (
  jobId: string,
): Promise<KnowledgeIngestionJob> =>
  getJson<KnowledgeIngestionJob>(
    API_ENDPOINTS.knowledgeBase.ingestionJob(jobId),
    "Không thể tải tiến độ ingest",
  );

export const failKnowledgeIngestionJob = async (
  jobId: string,
  errorMessage: string,
): Promise<KnowledgeIngestionJob> =>
  postJson<KnowledgeIngestionJob>(
    API_ENDPOINTS.knowledgeBase.failIngestionJob(jobId),
    { errorMessage },
    "Không thể cập nhật trạng thái ingest thất bại",
  );

export const unpublishKnowledgeBaseEntry = async (
  entryId: string,
): Promise<KnowledgeBaseVersion> =>
  postJson<KnowledgeBaseVersion>(
    API_ENDPOINTS.knowledgeBase.unpublish(entryId),
    {},
    "Không thể unpublish knowledge base",
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
