import { API_ENDPOINTS } from "../config/api";
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
} from "../types/legalTicket";
import type { LegalTicketStatus } from "../types/legalTicketStatus";
import { normalizeLegalTicketStatus } from "../types/legalTicketStatus";
import { buildAuthHeaders, requestApiData } from "./http";

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
  riskLevel?: string,
): Promise<PageResponse<LegalTicket>> =>
  getJson<PageResponse<LegalTicket>>(
    `${API_ENDPOINTS.legalTickets.adminList}?${buildPageQuery(page, size, {
      status: getLegalTicketStatusQueryParam(status),
      riskLevel,
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
