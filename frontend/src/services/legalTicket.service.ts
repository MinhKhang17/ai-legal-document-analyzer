import { API_ENDPOINTS } from "../config/api";
import type { CreateLegalTicketRequest, LegalTicket } from "../types/legalTicket";
import { buildAuthHeaders, requestApiData } from "./http";

export const createLegalTicket = async (
  payload: CreateLegalTicketRequest,
): Promise<LegalTicket> =>
  requestApiData<LegalTicket>(
    API_ENDPOINTS.legalTickets.create,
    {
      method: "POST",
      headers: buildAuthHeaders({
        Accept: "application/json",
        "Content-Type": "application/json",
      }),
      credentials: "include",
      body: JSON.stringify(payload),
    },
    "Không thể tạo ticket luật sư",
  );

export const getLegalTicket = async (ticketId: string): Promise<LegalTicket> =>
  requestApiData<LegalTicket>(
    API_ENDPOINTS.legalTickets.detail(ticketId),
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể tải legal ticket",
  );

export const getAdminLegalTickets = async (): Promise<LegalTicket[]> =>
  requestApiData<LegalTicket[]>(
    API_ENDPOINTS.legalTickets.adminList,
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể tải danh sách legal ticket",
  );

export const assignLawyerToLegalTicket = async (
  ticketId: string,
  lawyerId: string,
): Promise<LegalTicket> =>
  requestApiData<LegalTicket>(
    `${API_ENDPOINTS.legalTickets.assignLawyer(ticketId)}?lawyerId=${encodeURIComponent(lawyerId)}`,
    {
      method: "POST",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể assign lawyer cho legal ticket",
  );
