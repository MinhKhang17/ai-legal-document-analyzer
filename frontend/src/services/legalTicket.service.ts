import { API_ENDPOINTS, buildApiUrl } from "../config/api";
import type {
  AdminChatHistory,
  AdminTicketFile,
  AssignLawyerRequest,
  CancelLegalTicketRequest,
  CloseLegalTicketRequest,
  CreateLegalTicketRequest,
  CustomerTicketReplyRequest,
  LegalTicket,
  LegalTicketMessage,
  PageResponse,
  RejectLegalTicketRequest,
  ReopenLegalTicketRequest,
  TicketSummary,
  AttachmentPolicy,
  TicketAttachment,
  ConversationShare,
  ConversationScope,
} from "../types/legalTicket";
import type { LegalTicketStatus } from "../types/legalTicketStatus";
import type { LegalTicketRiskLevel, LegalTicketType } from "../types/legalTicket";
import { normalizeLegalTicketStatus } from "../types/legalTicketStatus";
import { buildAuthHeaders, requestApiData } from "./http";
import { getAccessToken } from "./authSession";

const jsonHeaders = {
  Accept: "application/json",
  "Content-Type": "application/json",
};

const buildPageQuery = (
  page: number,
  size: number,
  extra: Record<string, string | undefined> = {},
) => {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  Object.entries(extra).forEach(([key, value]) => {
    if (value && value.trim().length > 0) {
      query.set(key, value);
    }
  });

  return query.toString();
};

const getLegalTicketStatusQueryParam = (
  status?: string | null,
): LegalTicketStatus | undefined => {
  const trimmedStatus = status?.trim();

  if (!trimmedStatus || trimmedStatus.toUpperCase() === "ALL") {
    return undefined;
  }

  const normalizedStatus = normalizeLegalTicketStatus(trimmedStatus);

  if (!normalizedStatus && import.meta.env.DEV) {
    console.warn(`Ignored invalid legal ticket status filter: ${trimmedStatus}`);
  }

  return normalizedStatus;
};

const getJson = <TResponse>(
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

const postJson = <TResponse>(
  endpoint: string,
  payload: object | undefined,
  errorMessage: string,
): Promise<TResponse> =>
  requestApiData<TResponse>(
    endpoint,
    {
      method: "POST",
      headers: buildAuthHeaders(jsonHeaders),
      credentials: "include",
      body: payload ? JSON.stringify(payload) : undefined,
    },
    errorMessage,
  );

export const createLegalTicket = async (
  payload: CreateLegalTicketRequest,
): Promise<LegalTicket> =>
  postJson<LegalTicket>(
    API_ENDPOINTS.legalTickets.create,
    payload,
    "Không thể tạo ticket luật sư",
  );

export const getMyLegalTickets = async (
  page = 0,
  size = 10,
  status?: LegalTicketStatus,
): Promise<PageResponse<LegalTicket>> =>
  getJson<PageResponse<LegalTicket>>(
    `${API_ENDPOINTS.legalTickets.my}?${buildPageQuery(page, size, {
      status: getLegalTicketStatusQueryParam(status),
    })}`,
    "Không thể tải danh sách ticket của tôi",
  );

export const getLegalTicket = async (ticketId: string): Promise<LegalTicket> =>
  getJson<LegalTicket>(
    API_ENDPOINTS.legalTickets.detail(ticketId),
    "Không thể tải legal ticket",
  );

export const getLegalTicketMessages = async (
  ticketId: string,
): Promise<LegalTicketMessage[]> =>
  getJson<LegalTicketMessage[]>(
    API_ENDPOINTS.legalTickets.messages(ticketId),
    "Không thể tải tin nhắn legal ticket",
  );

export const cancelLegalTicket = async (
  ticketId: string,
  payload?: CancelLegalTicketRequest,
): Promise<LegalTicket> =>
  postJson<LegalTicket>(
    API_ENDPOINTS.legalTickets.cancel(ticketId),
    payload,
    "Không thể hủy legal ticket",
  );

export const replyToLegalTicket = async (
  ticketId: string,
  payload: CustomerTicketReplyRequest,
): Promise<LegalTicket> =>
  postJson<LegalTicket>(
    API_ENDPOINTS.legalTickets.customerReply(ticketId),
    payload,
    "Không thể gửi phản hồi legal ticket",
  );

export const closeLegalTicket = async (
  ticketId: string,
  payload?: CloseLegalTicketRequest,
): Promise<LegalTicket> =>
  postJson<LegalTicket>(
    API_ENDPOINTS.legalTickets.close(ticketId),
    payload,
    "Không thể đóng legal ticket",
  );

export const reopenLegalTicket = async (
  ticketId: string,
  payload: ReopenLegalTicketRequest,
): Promise<LegalTicket> =>
  postJson<LegalTicket>(
    API_ENDPOINTS.legalTickets.reopen(ticketId),
    payload,
    "Không thể mở lại legal ticket",
  );

export const getAdminLegalTickets = async (
  page = 0,
  size = 10,
  status?: LegalTicketStatus,
  riskLevel?: LegalTicketRiskLevel,
  ticketType?: LegalTicketType,
): Promise<PageResponse<LegalTicket>> =>
  getJson<PageResponse<LegalTicket>>(
    `${API_ENDPOINTS.legalTickets.adminList}?${buildPageQuery(page, size, {
      status: getLegalTicketStatusQueryParam(status),
      riskLevel,
      ticketType,
    })}`,
    "Không thể tải danh sách legal ticket admin",
  );

export const getAdminLegalTicket = async (
  ticketId: string,
): Promise<LegalTicket> =>
  getJson<LegalTicket>(
    API_ENDPOINTS.legalTickets.adminDetail(ticketId),
    "Không thể tải chi tiết legal ticket admin",
  );

export const getAdminTicketSummary = async (
  ticketId: string,
): Promise<TicketSummary> =>
  getJson<TicketSummary>(
    API_ENDPOINTS.legalTickets.adminSummary(ticketId),
    "Không thể tải AI summary của ticket",
  );

export const getAdminTicketChatHistory = async (
  ticketId: string,
): Promise<AdminChatHistory> =>
  getJson<AdminChatHistory>(
    API_ENDPOINTS.legalTickets.adminChatHistory(ticketId),
    "Không thể tải lịch sử chat của ticket",
  );

export const getAdminTicketFiles = async (
  ticketId: string,
): Promise<AdminTicketFile[]> =>
  getJson<AdminTicketFile[]>(
    API_ENDPOINTS.legalTickets.adminFiles(ticketId),
    "Không thể tải file của ticket",
  );

export const assignLawyerToLegalTicket = async (
  ticketId: string,
  payload: AssignLawyerRequest,
): Promise<LegalTicket> =>
  postJson<LegalTicket>(
    API_ENDPOINTS.legalTickets.assignLawyer(ticketId),
    payload,
    "Không thể assign lawyer cho legal ticket",
  );

export const reassignLawyerToLegalTicket = async (
  ticketId: string,
  payload: AssignLawyerRequest,
): Promise<LegalTicket> =>
  postJson<LegalTicket>(
    API_ENDPOINTS.legalTickets.reassignLawyer(ticketId),
    payload,
    "Không thể reassign lawyer cho legal ticket",
  );

export const rejectLegalTicket = async (
  ticketId: string,
  payload: RejectLegalTicketRequest,
): Promise<LegalTicket> =>
  postJson<LegalTicket>(
    API_ENDPOINTS.legalTickets.reject(ticketId),
    payload,
    "Không thể từ chối legal ticket",
  );

export const classifyExpertTicket = (ticketId: string, payload: {
  complexity: "BASIC" | "STANDARD" | "COMPLEX" | "OUT_OF_SCOPE";
  reason: string;
  proposedExpertId: number;
  pricingType: "PLAN_INCLUDED" | "PAID";
  userPrice: number;
  internalTicketValue: number;
}): Promise<LegalTicket> => postJson<LegalTicket>(
  `/api/v1/admin/tickets/${encodeURIComponent(ticketId)}/classify`, payload, "Không thể phân loại ticket");

export const confirmExpertTicketPayment = (ticketId: string, paymentReference: string): Promise<LegalTicket> =>
  postJson<LegalTicket>(`/api/v1/admin/tickets/${encodeURIComponent(ticketId)}/confirm-payment`,
    { paymentReference }, "Không thể xác nhận thanh toán");

export const decideExpertTicketQuote = (ticketId: string, decision: "ACCEPT" | "REJECT"): Promise<LegalTicket> =>
  postJson<LegalTicket>(`/api/v1/legal-tickets/${encodeURIComponent(ticketId)}/quote-decision`,
    { decision }, "Không thể cập nhật báo giá");

export const extendExpertTicketSla = (
  ticketId: string,
  hours: number,
  reason: string,
): Promise<LegalTicket> => postJson<LegalTicket>(
  `/api/v1/admin/tickets/${encodeURIComponent(ticketId)}/extend-sla`,
  { hours, reason },
  "Không thể gia hạn SLA",
);

export const getAttachmentPolicy = (): Promise<AttachmentPolicy> =>
  getJson<AttachmentPolicy>("/api/config/attachment-policy", "Không thể tải giới hạn file đính kèm");

export const uploadTicketAttachment = (
  file: File,
  ownerId: string,
  onProgress: (percent: number) => void,
): Promise<TicketAttachment> => new Promise((resolve, reject) => {
  const form = new FormData();
  form.append("file", file);
  const xhr = new XMLHttpRequest();
  xhr.open("POST", buildApiUrl(`/api/attachments?ownerId=${encodeURIComponent(ownerId)}`));
  const token = getAccessToken();
  if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
  xhr.withCredentials = true;
  xhr.upload.onprogress = (event) => {
    if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100));
  };
  xhr.onerror = () => reject(new Error(`Không thể upload ${file.name}`));
  xhr.onload = () => {
    try {
      const body = JSON.parse(xhr.responseText) as { data?: TicketAttachment; message?: string };
      if (xhr.status >= 200 && xhr.status < 300 && body.data) resolve(body.data);
      else reject(new Error(body.message || `Không thể upload ${file.name}`));
    } catch { reject(new Error(`Không thể upload ${file.name}`)); }
  };
  xhr.send(form);
});

export const removeTicketAttachment = async (attachmentId: string): Promise<void> => {
  await requestApiData<unknown>(`/api/attachments/${attachmentId}`, {
    method: "DELETE", headers: buildAuthHeaders({ Accept: "application/json" }), credentials: "include",
  }, "Không thể xóa file đính kèm");
};

export const downloadTicketAttachment = async (attachmentId: string): Promise<string> => {
  const response = await fetch(buildApiUrl(`/api/attachments/${attachmentId}/download`), {
    method: "GET",
    headers: buildAuthHeaders({ Accept: "application/octet-stream" }),
    credentials: "include",
  });
  if (!response.ok) throw new Error("Không thể tải file đính kèm");
  return URL.createObjectURL(await response.blob());
};

export const getTicketConversation = (ticketId: string, page = 0, size = 30): Promise<PageResponse<LegalTicketMessage>> =>
  getJson<PageResponse<LegalTicketMessage>>(`/api/tickets/${ticketId}/messages?page=${page}&size=${size}`, "Không thể tải hội thoại ticket");

export const sendTicketConversationMessage = (ticketId: string, content: string, attachmentIds: string[], replyToMessageId?: string): Promise<LegalTicketMessage> =>
  postJson<LegalTicketMessage>(`/api/tickets/${ticketId}/messages`, { content, attachmentIds, replyToMessageId }, "Không thể gửi tin nhắn ticket");

export const createTicketConversationShare = (ticketId: string, scope: ConversationScope, expiresAt: string): Promise<ConversationShare> =>
  postJson<ConversationShare>(`/api/tickets/${ticketId}/shares`, { scope, expiresAt }, "Không thể tạo link chia sẻ");

export const getSharedTicketConversation = (token: string): Promise<LegalTicket> =>
  getJson<LegalTicket>(`/api/shared-conversation/${encodeURIComponent(token)}`, "Không thể mở cuộc hội thoại được chia sẻ");

export const approveLegalTicketInternal = async (ticketId: string): Promise<LegalTicket> =>
  postJson<LegalTicket>(API_ENDPOINTS.legalTickets.adminApprove(ticketId), undefined, "Khong the tiep nhan ticket");

export const closeLegalTicketInternal = async (ticketId: string, note?: string): Promise<LegalTicket> =>
  postJson<LegalTicket>(API_ENDPOINTS.legalTickets.adminClose(ticketId), { note }, "Khong the dong ticket");

export const getCustomerTicketFiles = async (ticketId: string): Promise<AdminTicketFile[]> =>
  getJson<AdminTicketFile[]>(API_ENDPOINTS.legalTickets.files(ticketId), "Không thể tải file của ticket");

export const downloadCustomerTicketFile = async (ticketId: string, documentId: string): Promise<string> => {
  const response = await fetch(buildApiUrl(API_ENDPOINTS.legalTickets.downloadFile(ticketId, documentId)), { method: 'GET', headers: buildAuthHeaders({ Accept: 'application/octet-stream' }), credentials: 'include' });
  if (!response.ok) throw new Error('Không thể tải file');
  return URL.createObjectURL(await response.blob());
};

export const downloadStaffDocument = async (documentId: string): Promise<string> => {
  const response = await fetch(buildApiUrl(API_ENDPOINTS.adminDocuments.download(documentId)), { method: 'GET', headers: buildAuthHeaders({ Accept: 'application/octet-stream' }), credentials: 'include' });
  if (!response.ok) throw new Error('Không thể tải tài liệu gốc');
  return URL.createObjectURL(await response.blob());
};
