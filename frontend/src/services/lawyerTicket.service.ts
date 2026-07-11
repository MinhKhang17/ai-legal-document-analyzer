import { API_ENDPOINTS, buildApiUrl } from "../config/api";
import type {
  CloseLawyerTicketRequest,
  CreateLawyerTicketMessageRequest,
  CreateLawyerTicketMessageResponse,
  LawyerTicketDetail,
  LawyerTicketFile,
  LawyerTicketMessage,
  LawyerTicketPageResponse,
  RequestMoreInfoLawyerTicketRequest,
  ResolveLawyerTicketRequest,
  UploadLawyerTicketFileRequest,
} from "../types/lawyerTicket";
import type { LegalTicket } from "../types/legalTicket";
import { buildAuthHeaders, requestApiData } from "./http";

const jsonHeaders = {
  Accept: "application/json",
  "Content-Type": "application/json",
};

const lawyerTicketActionEndpoint = (ticketId: string, action: string) =>
  `${API_ENDPOINTS.lawyerTickets.detail(ticketId)}/${action}`;

const postLawyerTicketAction = <TResponse>(
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

export const getMyLawyerTickets = async (page = 0, size = 10, status?: string): Promise<LawyerTicketPageResponse> => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status?.trim()) query.set('status', status.trim());
  return (
  requestApiData<LawyerTicketPageResponse>(
    `${API_ENDPOINTS.lawyerTickets.my}?${query.toString()}`,
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Khong the tai danh sach ticket cua lawyer",
  ));
};

export const getLawyerTicketDetail = async (
  ticketId: string,
): Promise<LawyerTicketDetail> =>
  requestApiData<LawyerTicketDetail>(
    API_ENDPOINTS.lawyerTickets.detail(ticketId),
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Khong the tai chi tiet ticket",
  );

export const getLawyerTicketMessages = async (
  ticketId: string,
): Promise<LawyerTicketMessage[]> =>
  requestApiData<LawyerTicketMessage[]>(
    API_ENDPOINTS.lawyerTickets.messages(ticketId),
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Khong the tai tin nhan ticket",
  );

export const sendLawyerTicketMessage = async (
  ticketId: string,
  payload: CreateLawyerTicketMessageRequest,
): Promise<CreateLawyerTicketMessageResponse> =>
  requestApiData<CreateLawyerTicketMessageResponse>(
    API_ENDPOINTS.lawyerTickets.messages(ticketId),
    {
      method: "POST",
      headers: buildAuthHeaders({
        Accept: "application/json",
        "Content-Type": "application/json",
      }),
      credentials: "include",
      body: JSON.stringify(payload),
    },
    "Khong the gui tin nhan",
  );

export const getLawyerTicketFiles = async (
  ticketId: string,
): Promise<LawyerTicketFile[]> =>
  requestApiData<LawyerTicketFile[]>(
    API_ENDPOINTS.lawyerTickets.files(ticketId),
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Khong the tai tep dinh kem",
  );

export const downloadLawyerTicketFile = async (
  ticketId: string,
  documentId: string,
): Promise<string> => {
  const response = await fetch(
    buildApiUrl(API_ENDPOINTS.lawyerTickets.download(ticketId, documentId)),
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/octet-stream" }),
      credentials: "include",
    },
  );

  if (!response.ok) {
    throw new Error("Khong the tai thong tin tep dinh kem");
  }

  const blob = await response.blob();
  return URL.createObjectURL(blob);
};

export const uploadLawyerTicketFile = async (
  ticketId: string,
  payload: UploadLawyerTicketFileRequest,
): Promise<LawyerTicketFile> =>
  requestApiData<LawyerTicketFile>(
    API_ENDPOINTS.lawyerTickets.files(ticketId),
    {
      method: "POST",
      headers: buildAuthHeaders({
        Accept: "application/json",
        "Content-Type": "application/json",
      }),
      credentials: "include",
      body: JSON.stringify(payload),
    },
    "Khong the tai tep len",
  );

export const closeLawyerTicket = async (
  ticketId: string,
  payload: CloseLawyerTicketRequest,
): Promise<LegalTicket> =>
  postLawyerTicketAction<LegalTicket>(
    API_ENDPOINTS.lawyerTickets.close(ticketId),
    payload,
    "Khong the dong ticket",
  );

export const startReviewLawyerTicket = async (
  ticketId: string,
): Promise<LegalTicket> =>
  postLawyerTicketAction<LegalTicket>(
    lawyerTicketActionEndpoint(ticketId, "start-review"),
    undefined,
    "Khong the bat dau xu ly ticket",
  );

export const requestMoreInfoLawyerTicket = async (
  ticketId: string,
  payload: RequestMoreInfoLawyerTicketRequest,
): Promise<LegalTicket> =>
  postLawyerTicketAction<LegalTicket>(
    lawyerTicketActionEndpoint(ticketId, "request-more-info"),
    payload,
    "Khong the gui yeu cau bo sung thong tin",
  );

export const resolveLawyerTicket = async (
  ticketId: string,
  payload: ResolveLawyerTicketRequest,
): Promise<LegalTicket> =>
  postLawyerTicketAction<LegalTicket>(
    lawyerTicketActionEndpoint(ticketId, "resolve"),
    payload,
    "Khong the gui ket luan xu ly ticket",
  );
