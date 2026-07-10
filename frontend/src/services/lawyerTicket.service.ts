import { API_ENDPOINTS } from "../config/api";
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

export const getMyLawyerTickets = async (): Promise<LawyerTicketPageResponse> =>
  requestApiData<LawyerTicketPageResponse>(
    API_ENDPOINTS.lawyerTickets.my,
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể tải danh sách ticket của lawyer",
  );

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
    "Không thể tải chi tiết ticket",
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
    "Không thể tải tin nhắn ticket",
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
    "Không thể gửi tin nhắn",
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
    "Không thể tải tệp đính kèm",
  );

export const downloadLawyerTicketFile = async (
  ticketId: string,
  documentId: string,
): Promise<LawyerTicketFile> =>
  requestApiData<LawyerTicketFile>(
    API_ENDPOINTS.lawyerTickets.download(ticketId, documentId),
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể tải thông tin tệp đính kèm",
  );

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
    "Không thể tải tệp lên",
  );

export const closeLawyerTicket = async (
  ticketId: string,
  payload: CloseLawyerTicketRequest,
): Promise<LegalTicket> =>
  postLawyerTicketAction<LegalTicket>(
    API_ENDPOINTS.lawyerTickets.close(ticketId),
    payload,
    "Không thể đóng ticket",
  );

export const startReviewLawyerTicket = async (
  ticketId: string,
): Promise<LegalTicket> =>
  postLawyerTicketAction<LegalTicket>(
    lawyerTicketActionEndpoint(ticketId, "start-review"),
    undefined,
    "Không thể bắt đầu xử lý ticket",
  );

export const requestMoreInfoLawyerTicket = async (
  ticketId: string,
  payload: RequestMoreInfoLawyerTicketRequest,
): Promise<LegalTicket> =>
  postLawyerTicketAction<LegalTicket>(
    lawyerTicketActionEndpoint(ticketId, "request-more-info"),
    payload,
    "Không thể gửi yêu cầu bổ sung thông tin",
  );

export const resolveLawyerTicket = async (
  ticketId: string,
  payload: ResolveLawyerTicketRequest,
): Promise<LegalTicket> =>
  postLawyerTicketAction<LegalTicket>(
    lawyerTicketActionEndpoint(ticketId, "resolve"),
    payload,
    "Không thể gửi kết luận xử lý ticket",
  );
