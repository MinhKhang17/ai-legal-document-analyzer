import { API_ENDPOINTS } from "../config/api";
import type {
  AdminRevenueOverview,
  ExpertRevenueSummary,
  ExpertRevenueTicket,
  ExpertRevenueTicketPage,
  RevenueSetting,
  UpdateExpertPaymentRequest,
} from "../types/expertRevenue";
import { buildAuthHeaders, requestApiData } from "./http";

const jsonHeaders = { Accept: "application/json", "Content-Type": "application/json" };

export const getExpertRevenueSummary = (): Promise<ExpertRevenueSummary> =>
  requestApiData(API_ENDPOINTS.expertRevenue.summary, {
    method: "GET", headers: buildAuthHeaders({ Accept: "application/json" }), credentials: "include",
  }, "Không thể tải tổng quan doanh thu chuyên gia");

export const getExpertRevenueTickets = (page = 0, size = 10): Promise<ExpertRevenueTicketPage> => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return requestApiData(`${API_ENDPOINTS.expertRevenue.tickets}?${query}`, {
    method: "GET", headers: buildAuthHeaders({ Accept: "application/json" }), credentials: "include",
  }, "Không thể tải doanh thu theo ticket");
};

export const updateExpertPayment = (
  ticketId: string,
  payload: UpdateExpertPaymentRequest,
): Promise<ExpertRevenueTicket> =>
  requestApiData(API_ENDPOINTS.expertRevenue.updatePayment(ticketId), {
    method: "PATCH", headers: buildAuthHeaders(jsonHeaders), credentials: "include", body: JSON.stringify(payload),
  }, "Không thể cập nhật thanh toán chuyên gia");

export const resetExpertPayment = (ticketId: string): Promise<ExpertRevenueTicket> =>
  requestApiData(API_ENDPOINTS.expertRevenue.resetPayment(ticketId), {
    method: "POST", headers: buildAuthHeaders({ Accept: "application/json" }), credentials: "include",
  }, "Không thể reset dữ liệu thanh toán chuyên gia");

export const getRevenueSetting = (): Promise<RevenueSetting> =>
  requestApiData(API_ENDPOINTS.adminRevenue.settings, {
    method: "GET", headers: buildAuthHeaders({ Accept: "application/json" }), credentials: "include",
  }, "Không thể tải cấu hình hoa hồng");

export const updateRevenueSetting = (commissionRate: number): Promise<RevenueSetting> =>
  requestApiData(API_ENDPOINTS.adminRevenue.settings, {
    method: "PATCH", headers: buildAuthHeaders(jsonHeaders), credentials: "include",
    body: JSON.stringify({ commissionRate }),
  }, "Không thể cập nhật cấu hình hoa hồng");

export const getAdminRevenueOverview = (): Promise<AdminRevenueOverview> =>
  requestApiData(API_ENDPOINTS.adminRevenue.overview, {
    method: "GET", headers: buildAuthHeaders({ Accept: "application/json" }), credentials: "include",
  }, "Không thể tải tổng quan doanh thu hệ thống");
