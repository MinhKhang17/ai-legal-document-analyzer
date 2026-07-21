import { API_ENDPOINTS } from "../config/api";
import { buildAuthHeaders, requestApiData, requestApiResponse } from "./http";

export interface BackendUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  active: boolean;
  emailVerified?: boolean;
  mustChangePassword?: boolean;
  passwordResetDeadline?: string | null;
  specialty?: string | null;
  legalDomain?: string | null;
  description?: string | null;
}

export interface CreateExpertRequest {
  firstName: string;
  lastName: string;
  email: string;
  active?: boolean;
  specialty?: string;
  legalDomain?: string;
  description?: string;
}

export const getUsers = async (): Promise<BackendUser[]> =>
  requestApiData<BackendUser[]>(
    API_ENDPOINTS.users.list,
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể tải danh sách người dùng",
  );

export const getUserDetail = async (userId: number | string): Promise<BackendUser> =>
  requestApiData<BackendUser>(
    API_ENDPOINTS.users.detail(userId),
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể tải chi tiết người dùng",
  );

export const getAdminExperts = async (): Promise<BackendUser[]> =>
  requestApiData<BackendUser[]>(API_ENDPOINTS.users.adminExperts, {
    method: "GET",
    headers: buildAuthHeaders({ Accept: "application/json" }),
    credentials: "include",
  }, "Không thể tải danh sách chuyên gia");

export const createExpert = async (payload: CreateExpertRequest): Promise<BackendUser> =>
  requestApiData<BackendUser>(API_ENDPOINTS.users.adminExperts, {
    method: "POST",
    headers: buildAuthHeaders({ "Content-Type": "application/json" }),
    credentials: "include",
    body: JSON.stringify(payload),
  }, "Không thể tạo tài khoản chuyên gia");

export const resendExpertActivation = async (email: string): Promise<void> =>
  requestApiResponse<void>(API_ENDPOINTS.users.resendExpertActivation, {
    method: "POST",
    headers: buildAuthHeaders({ "Content-Type": "application/json" }),
    credentials: "include",
    body: JSON.stringify({ email }),
  }, "Không thể gửi lại thông tin kích hoạt").then(() => undefined);

export const changePassword = async (payload: { oldPassword: string; newPassword: string; confirmNewPassword: string }): Promise<void> =>
  requestApiResponse<void>(API_ENDPOINTS.users.changePassword, {
    method: "POST",
    headers: buildAuthHeaders({ "Content-Type": "application/json" }),
    credentials: "include",
    body: JSON.stringify(payload),
  }, "Không thể đổi mật khẩu").then(() => undefined);

export const deleteUser = async (userId: number | string): Promise<void> =>
  requestApiResponse<void>(API_ENDPOINTS.users.delete(userId), {
    method: "DELETE",
    headers: buildAuthHeaders({ Accept: "application/json" }),
    credentials: "include",
  }, "Không thể xóa/vô hiệu hóa tài khoản người dùng").then(() => undefined);

export const restoreUser = async (userId: number | string): Promise<void> =>
  requestApiResponse<void>(API_ENDPOINTS.users.restore(userId), {
    method: "POST",
    headers: buildAuthHeaders({ Accept: "application/json" }),
    credentials: "include",
  }, "Không thể khôi phục tài khoản người dùng").then(() => undefined);
