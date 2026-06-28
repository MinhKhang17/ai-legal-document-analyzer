import { API_ENDPOINTS } from "../config/api";
import type {
  LawyerTicket,
  LawyerTicketPageResponse,
  LawyerTicketDetail,
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
      headers: buildAuthHeaders({
        Accept: "application/json",
      }),
      credentials: "include",
    },
    "Không thể tải chi tiết ticket",
  );