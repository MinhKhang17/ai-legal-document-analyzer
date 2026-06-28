import { API_ENDPOINTS } from "../config/api";
import type {
  CloseLawyerTicketRequest,
  CreateLawyerTicketMessageRequest,
  CreateLawyerTicketMessageResponse,
  LawyerTicketDetail,
  LawyerTicketFile,
  LawyerTicketMessage,
  LawyerTicketPageResponse,
  UploadLawyerTicketFileRequest,
} from "../types/lawyerTicket";
import { buildAuthHeaders, requestApiData } from "./http";

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
): Promise<LawyerTicketDetail> =>
  requestApiData<LawyerTicketDetail>(
    API_ENDPOINTS.lawyerTickets.close(ticketId),
    {
      method: "POST",
      headers: buildAuthHeaders({
        Accept: "application/json",
        "Content-Type": "application/json",
      }),
      credentials: "include",
      body: JSON.stringify(payload),
    },
    "Không thể đóng ticket",
  );